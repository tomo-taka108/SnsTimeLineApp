/**
 * Playwright 設定（docs/14_e2e_test.md）。
 *
 * 2つの project に分ける:
 * - "e2e": 通常のシナリオテスト。frontend の dev サーバー(5173)に対して実行する
 * - "perf": ブラウザパフォーマンステスト。本番ビルド(vite preview, 4173)に対して実行する
 *
 * dev サーバーと preview を分けている理由は docs/14_e2e_test.md 5.1。
 * Vite の dev サーバーは非バンドルのESモジュールを都度変換して配信するため、
 * そこで測ったLCPは「Viteの速さ」であってアプリの速さではない。
 */

import { defineConfig, devices } from "@playwright/test";

const E2E_BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:5173";
const PERF_BASE_URL = process.env.E2E_PERF_BASE_URL ?? "http://localhost:4173";

export default defineConfig({
  testDir: ".",
  globalSetup: "./global-setup.ts",
  outputDir: "./results/test-results",
  fullyParallel: true,
  // CI が存在しない現状は手動運用（13章 6章 #7 と同じ判断）。retries は
  // ローカルの一時的なノイズ対策として1回だけ許容する
  retries: 1,
  reporter: [
    ["list"],
    ["html", { outputFolder: "./results/playwright-report", open: "never" }],
  ],
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "e2e",
      testDir: "./specs",
      use: { ...devices["Desktop Chrome"], baseURL: E2E_BASE_URL },
    },
    {
      name: "perf",
      testDir: "./perf",
      // 性能計測は並列実行させない。CPUを分け合うと数値の意味が崩れる(docs/14_e2e_test.md 5.5)
      fullyParallel: false,
      use: { ...devices["Desktop Chrome"], baseURL: PERF_BASE_URL },
    },
  ],
});
