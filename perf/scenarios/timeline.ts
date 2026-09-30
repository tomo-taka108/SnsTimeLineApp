/**
 * Phase 1: タイムライン取得の負荷試験（docs/13_performance_test.md）。
 *
 * 【なぜタイムラインが最初か】
 * 最も頻繁に呼ばれ、かつ docs/06_non_functional.md 1.2 に数値目標が
 * 明記されている唯一の中心API。これ1本で「専用DB・シード・自動掃除・
 * thresholds」の全部品が通るため、最小の労力で仕組み全体を検証できる。
 *
 * 【カーソルの扱い】
 * nextCursor は不透明な文字列として次のリクエストにそのまま渡す。
 * 中身を解釈してはならない（docs/05_api_design.md 2.1 の規約）。
 * シードには同一 created_at の投稿を4箇所仕込んであるので、
 * タイブレーカー（D-33）が壊れていれば重複・欠落として現れる。
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import type { Options } from 'k6/options';
import type { CursorPage, PostSummary } from '../lib/types.ts';
import { authHeaders, login, seedUserEmail } from '../lib/auth.ts';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

/** 1イテレーションで辿るページ数（無限スクロールを模す） */
const PAGES_PER_ITERATION = 3;

/** ページあたりの取得件数。06 1.2 の「タイムライン20件」に合わせる */
const PAGE_SIZE = 20;

/** 1ページ目だけを切り出して見るためのカスタムメトリクス */
const firstPageDuration = new Trend('timeline_first_page_duration', true);

export const options: Options = {
  stages: [
    { duration: '30s', target: 10 }, // 立ち上げ
    { duration: '60s', target: 30 }, // 増やす
    { duration: '60s', target: 30 }, // 維持（ここが本番）
    { duration: '30s', target: 0 },  // 落とす
  ],

  /**
   * 合否の基準。docs/06_non_functional.md 1.2 の数値をそのまま落とし込む。
   *
   * 「1秒以内」を p95 と解釈した根拠:
   * docs/12_logging_and_operations.md 7章が「p95レイテンシ … 06 の 1.2 の
   * 応答時間目標と対応させる」と書いており、リポジトリ内で唯一の解釈がこれ。
   * max を基準にすると GC 1回で落ちるため、ノイズを測ることになる。
   *
   * p99 も記録するが値は緩める（テール劣化の傾向を見るため）。
   */
  thresholds: {
    'http_req_duration{name:GET /timeline}': ['p(95)<1000', 'p(99)<2000'],
    'timeline_first_page_duration': ['p(95)<1000'],
    'http_req_failed': ['rate<0.01'],
    'checks': ['rate>0.99'],
  },

  // 立ち上げ直後の JIT ウォームアップと接続プール確立を p95 に混ぜない
  discardResponseBodies: false,
};

interface SetupData {
  token: string;
}

/**
 * 試験開始前に1回だけ走る。
 * ここで例外を投げると k6 の teardown() は呼ばれないが、
 * 後片付けは run-perf.sh の trap が担保しているので問題ない。
 */
export function setup(): SetupData {
  const token = login(BASE_URL, seedUserEmail(1));
  return { token };
}

export default function (data: SetupData): void {
  const headers = authHeaders(data.token);
  let cursor: string | null = null;
  const seenIds = new Set<number>();

  for (let page = 0; page < PAGES_PER_ITERATION; page++) {
    const url =
      `${BASE_URL}/timeline?tab=all&limit=${PAGE_SIZE}` +
      (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');

    const res = http.get(url, {
      headers,
      // タグを付けると threshold をこのリクエストだけに絞れる
      tags: { name: 'GET /timeline' },
    });

    const ok = check(res, {
      'timeline: status is 200': (r) => r.status === 200,
      'timeline: has items': (r) => {
        const body = r.json() as unknown as CursorPage<PostSummary>;
        return Array.isArray(body.items);
      },
    });

    if (!ok) break;

    if (page === 0) {
      firstPageDuration.add(res.timings.duration);
    }

    const body = res.json() as unknown as CursorPage<PostSummary>;

    // D-33 の退行検知。同一 created_at の投稿を4箇所仕込んであるので、
    // タイブレーカーが効いていなければページ跨ぎで同じ id が再登場する。
    let duplicated = false;
    for (const item of body.items) {
      if (seenIds.has(item.id)) duplicated = true;
      seenIds.add(item.id);
    }
    check(null, {
      'timeline: ページ跨ぎで重複しない（D-33）': () => !duplicated,
    });

    if (!body.hasNext || !body.nextCursor) break;
    cursor = body.nextCursor;
  }

  // 実ユーザーの閲覧間隔を模す。これが無いと非現実的な連打になる
  sleep(1);
}
