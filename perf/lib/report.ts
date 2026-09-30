/**
 * 試験結果のレポート出力（docs/13_performance_test.md）。
 *
 * k6 の標準サマリ（ターミナル出力）は人間が読むには十分だが、
 * 「あとで見返す」「他の人に共有する」には JSON の生データも
 * HTML レポートも要る。handleSummary() で3種類を同時に出す。
 *
 * 【k6-reporter について】
 * benc-uk/k6-reporter（k6 公式ドキュメントが紹介するコミュニティ製ツール）を使う。
 * https://github.com/benc-uk/k6-reporter
 *
 * バージョンをタグで固定している（`main` ではなく `3.0.4`）。
 * `main` を使うと、向こうの更新で見た目やAPIが予告なく変わりうる。
 * 固定シードでシードデータを決定論的にしている（perf/seed/）のと同じ理由で、
 * 「前回と比較する」道具自体が実行のたびに変わってはならない。
 *
 * 【オフライン時の注意】
 * このインポートはテスト実行時にネットワーク経由で取得される。
 * オフライン環境では handleSummary() の呼び出しが失敗するが、
 * これは**レポート生成だけ**の失敗であり、試験本体（thresholds の判定）
 * には影響しない（handleSummary は試験が終わった後に呼ばれるため）。
 */

import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

/** シナリオ名（HTML レポートのタイトルに使う） */
export interface ReportOptions {
  title: string;
}

/**
 * handleSummary() の中身。各シナリオファイルから呼ぶ。
 *
 * 3つの出力先:
 *   - stdout                : 従来通りターミナルに色付きで表示（人間がその場で見る）
 *   - perf/results/summary.json : 生データ。他のツールでの再集計用
 *   - perf/results/summary.html : 人間が見やすい HTML レポート
 *
 * 【パスについて】
 * k6 はリポジトリルート（run-perf.sh が cd する場所）から実行されるため、
 * ここは 'perf/results/...' と**リポジトリルート起点**で書く。
 * 'results/...' と書くと `perf/` の外（リポジトリ直下）に出てしまう。
 *
 * run-perf.sh は perf/results/ を作成してから k6 を実行するため、
 * このパスは常に存在する前提でよい。
 */
export function buildSummary(
  data: object,
  opts: ReportOptions
): Record<string, string> {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'perf/results/summary.json': JSON.stringify(data, null, 2),
    'perf/results/summary.html': htmlReport(data, { title: opts.title }),
  };
}
