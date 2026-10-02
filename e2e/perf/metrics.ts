/**
 * ブラウザ性能指標の計測（docs/14_e2e_test.md 5章）。
 *
 * PerformanceObserver / PerformanceNavigationTiming をブラウザ内で評価して
 * LCP・FCP・CLS・TTFBを取得する。すべて page.evaluate() 内で動かし、
 * 結果だけをNode側に返す（計測対象はブラウザのAPIそのものなので、
 * page.evaluate の外で計測する方法は無い）。
 */

import type { Page } from "@playwright/test";

export type BrowserMetrics = {
  /** Time To First Byte (ms)。サーバーの初速 */
  ttfb: number;
  /** First Contentful Paint (ms)。最初に何かが描画されるまで */
  fcp: number | null;
  /** Largest Contentful Paint (ms)。最大の要素が描画されるまで */
  lcp: number | null;
  /** Cumulative Layout Shift（無次元）。レイアウトのガタつき */
  cls: number;
  /** DOMContentLoaded (ms) */
  domContentLoaded: number;
  /** load イベント (ms) */
  load: number;
};

/**
 * CLS / LCP の PerformanceObserver を事前に仕込む（page.goto() より前に呼ぶこと）。
 *
 * `layout-shift` と `largest-contentful-paint` は、observer 登録前に発生した
 * エントリまで `buffered: true` で遡れる保証がブラウザによって異なるため、
 * addInitScript でナビゲーション開始前から observer を張り、window に結果を
 * 貯め続ける方式にする。LCP は「それ以上大きな要素が出てこない」ことが
 * 確定するまで値が更新され続けるため、計測時点での最後の値を採用する。
 */
export async function installPerfObservers(page: Page): Promise<void> {
  await page.addInitScript(() => {
    type PerfBag = { lcp: number | null; cls: number };
    (window as unknown as { __e2ePerf: PerfBag }).__e2ePerf = { lcp: null, cls: 0 };

    try {
      new PerformanceObserver((list) => {
        const entries = list.getEntries() as (PerformanceEntry & {
          renderTime?: number;
          loadTime?: number;
        })[];
        const last = entries[entries.length - 1];
        if (last) {
          (window as unknown as { __e2ePerf: PerfBag }).__e2ePerf.lcp =
            last.renderTime || last.loadTime || last.startTime;
        }
      }).observe({ type: "largest-contentful-paint", buffered: true });
    } catch {
      // 対応していないブラウザでは何もしない（Chromiumでは利用可能）
    }

    try {
      new PerformanceObserver((list) => {
        for (const entry of list.getEntries() as (PerformanceEntry & {
          value: number;
          hadRecentInput: boolean;
        })[]) {
          if (!entry.hadRecentInput) {
            (window as unknown as { __e2ePerf: PerfBag }).__e2ePerf.cls += entry.value;
          }
        }
      }).observe({ type: "layout-shift", buffered: true });
    } catch {
      // 対応していないブラウザでは何もしない
    }
  });
}

/**
 * installPerfObservers が貯めた値（LCP/CLS）と、Navigation Timing から
 * 直接取れる値（TTFB/FCP/DOMContentLoaded/Load）をまとめて読み出す。
 *
 * ページの読み込みが落ち着いた後（例: 主要コンテンツの描画を待った後）に呼ぶこと。
 */
export async function collectBrowserMetrics(page: Page): Promise<BrowserMetrics> {
  return page.evaluate(() => {
    type PerfBag = { lcp: number | null; cls: number };
    const bag = (window as unknown as { __e2ePerf?: PerfBag }).__e2ePerf ?? { lcp: null, cls: 0 };

    const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    const fcpEntry = performance.getEntriesByName("first-contentful-paint")[0];

    return {
      ttfb: nav ? nav.responseStart - nav.requestStart : 0,
      fcp: fcpEntry ? fcpEntry.startTime : null,
      lcp: bag.lcp,
      cls: bag.cls,
      domContentLoaded: nav ? nav.domContentLoadedEventEnd - nav.startTime : 0,
      load: nav ? nav.loadEventEnd - nav.startTime : 0,
    };
  });
}
