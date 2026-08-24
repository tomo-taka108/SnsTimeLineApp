/**
 * 日時の表示ヘルパー（mockup/mock.js の formatRelative / formatAbsolute を移植）。
 *
 * date系ライブラリを増やさない方針（frontend/README.md）のため手書きする。
 */

/** 24時間以内は相対表示、それ以降は「8月15日」（docs/03_screen_design.md 5.1） */
export function formatRelative(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return "たった今";
  if (min < 60) return `${min}分前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}時間前`;
  const d = new Date(iso);
  return `${d.getMonth() + 1}月${d.getDate()}日`;
}

/** 投稿詳細用の絶対表示「2026年8月15日 14:32」（docs/03_screen_design.md SC-04） */
export function formatAbsolute(iso: string): string {
  const d = new Date(iso);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${hh}:${mm}`;
}
