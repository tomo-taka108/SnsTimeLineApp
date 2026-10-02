/**
 * ページ読み込み直後の共通待機（docs/14_e2e_test.md 4.4）。
 *
 * RequireAuth / RedirectIfAuthed が isRestoring の間スピナーを出し、
 * GET /auth/me の完了まで本文が無い（frontend/src/auth/RequireAuth.tsx）。
 * page.waitForTimeout() は使わず、スピナーが消えるのを待つ。
 */

import type { Page } from "@playwright/test";

export async function waitForAppReady(page: Page): Promise<void> {
  await page.locator(".page-center > .spinner").waitFor({ state: "detached" }).catch(() => {
    // スピナーが最初から存在しない（復元が一瞬で終わった）場合は無視する
  });
}
