/**
 * ブラウザ性能計測の結果出力（docs/14_e2e_test.md 5章）。
 *
 * perf/lib/report.ts（k6側、JSON + HTML を出す）と役割を揃えるが、
 * Playwright は公式HTMLレポータを既に持つため、ここでは JSON と
 * 人間が読むMarkdown表だけを出す（HTML生成の外部ライブラリは要らない）。
 */

import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import type { BrowserMetrics } from "./metrics.ts";

export type PageMeasurement = {
  page: string;
  /** 5回分の生データ */
  samples: BrowserMetrics[];
  /** 各指標の中央値。1回だけだとGC・ディスクキャッシュ等でぶれるため中央値を採る */
  median: BrowserMetrics;
};

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

export function computeMedian(samples: BrowserMetrics[]): BrowserMetrics {
  const nonNullLcp = samples.map((s) => s.lcp).filter((v): v is number => v !== null);
  const nonNullFcp = samples.map((s) => s.fcp).filter((v): v is number => v !== null);
  return {
    ttfb: median(samples.map((s) => s.ttfb)),
    fcp: nonNullFcp.length > 0 ? median(nonNullFcp) : null,
    lcp: nonNullLcp.length > 0 ? median(nonNullLcp) : null,
    cls: median(samples.map((s) => s.cls)),
    domContentLoaded: median(samples.map((s) => s.domContentLoaded)),
    load: median(samples.map((s) => s.load)),
  };
}

const RESULTS_DIR = path.resolve(import.meta.dirname, "../results");

/**
 * JSON（生データ）とMarkdown（人間が読む表）の両方を書き出す。
 *
 * perf/results/ と同じく .gitignore 済みで、実行のたびに上書きされる。
 * 長期的な記録は docs/14_e2e_test.md 5章のベースライン表に手動で転記する
 * （perf側の docs/13_performance_test.md 4.5 と同じ運用）。
 */
export async function writeReport(measurements: PageMeasurement[]): Promise<void> {
  await mkdir(RESULTS_DIR, { recursive: true });

  await writeFile(
    path.join(RESULTS_DIR, "perf-metrics.json"),
    JSON.stringify(measurements, null, 2),
    "utf-8",
  );

  const lines: string[] = [];
  lines.push("# ブラウザパフォーマンステスト結果");
  lines.push("");
  lines.push(`計測日時: ${new Date().toISOString()}`);
  lines.push("");
  lines.push(
    "> 絶対値をSLA判定に使わないこと。ブラウザ・被試験アプリ・DBが同一マシン上でCPUを奪い合うため、" +
      "数値は「このマシンでこの構成のとき」の値でしかない。前回との相対比較（退行検知）に使う" +
      "（docs/14_e2e_test.md 0.2）。",
  );
  lines.push("");

  for (const m of measurements) {
    lines.push(`## ${m.page}`);
    lines.push("");
    lines.push("| 指標 | 中央値(5回) | 全サンプル |");
    lines.push("|---|---|---|");
    lines.push(`| TTFB | ${m.median.ttfb.toFixed(1)}ms | ${m.samples.map((s) => s.ttfb.toFixed(1)).join(", ")} |`);
    lines.push(
      `| FCP | ${m.median.fcp?.toFixed(1) ?? "-"}ms | ${m.samples.map((s) => s.fcp?.toFixed(1) ?? "-").join(", ")} |`,
    );
    lines.push(
      `| **LCP** | **${m.median.lcp?.toFixed(1) ?? "-"}ms** | ${m.samples.map((s) => s.lcp?.toFixed(1) ?? "-").join(", ")} |`,
    );
    lines.push(`| CLS | ${m.median.cls.toFixed(3)} | ${m.samples.map((s) => s.cls.toFixed(3)).join(", ")} |`);
    lines.push(
      `| DOMContentLoaded | ${m.median.domContentLoaded.toFixed(1)}ms | ${m.samples.map((s) => s.domContentLoaded.toFixed(1)).join(", ")} |`,
    );
    lines.push(`| Load | ${m.median.load.toFixed(1)}ms | ${m.samples.map((s) => s.load.toFixed(1)).join(", ")} |`);
    lines.push("");
  }

  await writeFile(path.join(RESULTS_DIR, "perf-metrics.md"), lines.join("\n"), "utf-8");
}
