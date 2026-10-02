/**
 * ログイン済み context を作る fixture（docs/14_e2e_test.md 3.1）。
 *
 * 2種類を用意する:
 * - `user`: 各テストでその場でAPI経由で1人作る。書き込みを伴うテストはこれを使う
 * - `secondUser`: 2ユーザーシナリオ用に、独立した BrowserContext でもう1人作る
 *
 * 全テストでログイン済み storageState を共有しない理由:
 * リフレッシュトークンは使い捨てローテーションで、並列ワーカーが同じものを
 * 同時に使うと同ファミリが失効する（frontend/src/api/client.ts:25-34）。
 * 各テストが独立ユーザーを使えば、この問題を構造的に避けられる。
 */

import { test as base, type BrowserContext, type Page } from "@playwright/test";
import { createPost, createPosts, signup, type SignedUpUser } from "./api.ts";

type AuthFixtures = {
  /** ログイン済みの1人目のユーザーと、そのページ */
  user: SignedUpUser;
  /** user が既にログインした状態のページ */
  page: Page;
  /**
   * 2ユーザーシナリオ用の2人目。独立した BrowserContext を持つため、
   * localStorage が user とは完全に分離される。
   */
  secondUser: { user: SignedUpUser; context: BrowserContext; page: Page };
};

/** localStorage にトークンを書き込んでログイン状態を作る(docs/14_e2e_test.md 3.1) */
async function loginAs(page: Page, signedUp: SignedUpUser): Promise<void> {
  // frontend/src/api/tokenStorage.ts のキー名と一致させる
  await page.addInitScript(
    ({ accessToken, refreshToken }: { accessToken: string; refreshToken: string }) => {
      window.localStorage.setItem("snstimeline.accessToken", accessToken);
      window.localStorage.setItem("snstimeline.refreshToken", refreshToken);
    },
    { accessToken: signedUp.accessToken, refreshToken: signedUp.refreshToken },
  );
}

export const test = base.extend<AuthFixtures>({
  user: async ({}, use, testInfo) => {
    const signedUp = await signup("user", testInfo.parallelIndex);
    await use(signedUp);
  },

  // eslint 的には page を上書きするが、Playwright の fixture 機構ではこれが正しい拡張方法
  page: async ({ page, user }, use) => {
    await loginAs(page, user);
    // addInitScript はこの後の goto から効く。最初の goto を呼ぶのは spec 側の責務
    await use(page);
  },

  secondUser: async ({ browser, user: _user }, use, testInfo) => {
    const signedUp = await signup("second", testInfo.parallelIndex);
    const context = await browser.newContext();
    const page = await context.newPage();
    await loginAs(page, signedUp);
    await use({ user: signedUp, context, page });
    await context.close();
  },
});

export { expect } from "@playwright/test";
export { createPost, createPosts };
