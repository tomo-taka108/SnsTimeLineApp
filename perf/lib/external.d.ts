/**
 * k6 は URL を直接 import できるが、tsc（Node向けの型検査）はこれを解決できない。
 * ここに最小限の型だけ宣言しておく（実体は実行時に k6 が URL から取得する）。
 *
 * 対象は perf/lib/report.ts が使う2つのコミュニティライブラリのみ。
 * バージョンをタグ固定している理由は report.ts のコメントを参照。
 */

declare module 'https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js' {
  export function htmlReport(data: object, options?: { title?: string; theme?: string }): string;
}

declare module 'https://jslib.k6.io/k6-summary/0.1.0/index.js' {
  export function textSummary(
    data: object,
    options?: { indent?: string; enableColors?: boolean }
  ): string;
}
