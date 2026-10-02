/**
 * ブラウザパフォーマンステスト（docs/14_e2e_test.md 5章）。
 *
 * 測定対象は本番ビルド（vite preview）。dev サーバーは測らない
 * （Vite の dev サーバーは非バンドルのESモジュールを都度変換して配信するため、
 * そこで測ったLCPは「Viteの速さ」であってアプリの速さではない）。
 *
 * 閾値は Core Web Vitals の "Good" 基準（LCP < 2500ms, CLS < 0.1）を出発点にする。
 * プロジェクト独自の目標が06章に存在しない以上、業界標準が妥当な出発点のため。
 *
 * 絶対値をSLA判定の根拠にしない（docs/13_performance_test.md 0.2 / D-69 と同じ方針）。
 * 同一マシンでの測定はノイズを受けるため、5回計測して中央値を採る。
 */

import { expect, test } from "@playwright/test";
import { createPost, signup } from "../fixtures/api.ts";
import { collectBrowserMetrics, installPerfObservers, type BrowserMetrics } from "./metrics.ts";
import { computeMedian, writeReport, type PageMeasurement } from "./report.ts";

const SAMPLE_COUNT = 5;
const LCP_THRESHOLD_MS = 2500;
const CLS_THRESHOLD = 0.1;

/**
 * 1ページ分を SAMPLE_COUNT 回計測する。
 *
 * 毎回 installPerfObservers → goto → 主要コンテンツの描画待ち → 計測、を繰り返す。
 * ページ内遷移ではなく毎回 goto するのは、observer が初回ロードのLCP/CLSのみを
 * 対象にするため（SPA内遷移を含めると「何を測っているか」が曖昧になる）。
 */
async function measurePage(
  page: import("@playwright/test").Page,
  url: string,
  waitForReady: () => Promise<void>,
): Promise<BrowserMetrics[]> {
  const samples: BrowserMetrics[] = [];
  for (let i = 0; i < SAMPLE_COUNT; i++) {
    await installPerfObservers(page);
    await page.goto(url);
    await waitForReady();
    samples.push(await collectBrowserMetrics(page));
  }
  return samples;
}

test.describe("ブラウザパフォーマンス", () => {
  test("主要3ページのLCP/FCP/CLS/TTFBを計測する", async ({ page }, testInfo) => {
    const measurements: PageMeasurement[] = [];

    // /login（未認証・最小構成）
    const loginSamples = await measurePage(page, "/login", async () => {
      await page.getByRole("heading", { name: "ログイン" }).waitFor();
    });
    measurements.push({ page: "/login (SC-01)", samples: loginSamples, median: computeMedian(loginSamples) });

    // 認証済みユーザーと投稿を用意する（ブラウザ性能計測はE2Eシナリオと同じく
    // API経由でデータを作る。docs/14_e2e_test.md 3章・D-75と同じ方針）
    const user = await signup("perf", testInfo.parallelIndex);
    await page.addInitScript(
      ({ accessToken, refreshToken }: { accessToken: string; refreshToken: string }) => {
        window.localStorage.setItem("snstimeline.accessToken", accessToken);
        window.localStorage.setItem("snstimeline.refreshToken", refreshToken);
      },
      { accessToken: user.accessToken, refreshToken: user.refreshToken },
    );

    const post = await createPost(user.accessToken, "ブラウザ性能計測用の投稿です。");

    // /（認証済み・タイムライン）
    const timelineSamples = await measurePage(page, "/", async () => {
      await page.locator(".post-card").first().waitFor();
    });
    measurements.push({ page: "/ (SC-03)", samples: timelineSamples, median: computeMedian(timelineSamples) });

    // /posts/:id（認証済み・投稿詳細）
    const detailSamples = await measurePage(page, `/posts/${post.id}`, async () => {
      await page.locator(".detail-text").waitFor();
    });
    measurements.push({
      page: `/posts/${post.id} (SC-04)`,
      samples: detailSamples,
      median: computeMedian(detailSamples),
    });

    await writeReport(measurements);

    // 結果をターミナルにも出す（k6のサマリ表示に相当）
    // eslint的な縛りがないため console.log で構わない（Node側の出力）
    for (const m of measurements) {
      // eslint-disable-next-line no-console
      console.log(
        `[perf] ${m.page}: LCP=${m.median.lcp?.toFixed(1) ?? "-"}ms CLS=${m.median.cls.toFixed(3)} ` +
          `TTFB=${m.median.ttfb.toFixed(1)}ms FCP=${m.median.fcp?.toFixed(1) ?? "-"}ms`,
      );
    }

    // 閾値判定。基準を作ることが今回の目的であり、Core Web Vitalsの"Good"を出発点にする
    for (const m of measurements) {
      expect(m.median.lcp, `${m.page} のLCP`).not.toBeNull();
      expect(m.median.lcp ?? Infinity, `${m.page} のLCPが${LCP_THRESHOLD_MS}ms未満`).toBeLessThan(
        LCP_THRESHOLD_MS,
      );
      expect(m.median.cls, `${m.page} のCLSが${CLS_THRESHOLD}未満`).toBeLessThan(CLS_THRESHOLD);
    }
  });
});
