/**
 * 差別化ポイントの非表示確認（docs/14_e2e_test.md 4.2、#506-507）。
 *
 * docs/02_feature_list.md:239-241、docs/01_requirements.md 2.2 が明記する
 * 「このアプリに無いはずの機能」を機械的に守る。「無いこと」を検証する珍しいテストで、
 * うっかり足してしまう類の退行を検知する。
 */

import { expect } from "@playwright/test";
import { createPost } from "../fixtures/api.ts";
import { test } from "../fixtures/auth.ts";
import { waitForAppReady } from "../fixtures/wait.ts";

test.describe("差別化ポイントの非表示確認", () => {
  // #506: インプレッション数（閲覧数）がどこにも表示されない
  test("インプレッション数（閲覧数）がどこにも表示されない", async ({ page, user }) => {
    const post = await createPost(user.accessToken, `差別化確認用投稿 ${Date.now()}`);

    await page.goto("/");
    await waitForAppReady(page);
    await expect(page.locator(".post-card").first()).toBeVisible();
    await expect(page.getByText(/閲覧|インプレッション|views?/i)).toHaveCount(0);

    await page.goto(`/posts/${post.id}`);
    await waitForAppReady(page);
    await expect(page.getByText(/閲覧|インプレッション|views?/i)).toHaveCount(0);
  });

  // #507: リツイート / リポストのボタンがどこにも存在しない
  test("リツイート・リポストのボタンがどこにも存在しない", async ({ page, user }) => {
    const post = await createPost(user.accessToken, `差別化確認用投稿2 ${Date.now()}`);

    await page.goto("/");
    await waitForAppReady(page);
    await expect(page.locator(".post-card").first()).toBeVisible();
    await expect(page.getByRole("button", { name: /リツイート|リポスト|retweet|repost/i })).toHaveCount(0);

    await page.goto(`/posts/${post.id}`);
    await waitForAppReady(page);
    await expect(page.getByRole("button", { name: /リツイート|リポスト|retweet|repost/i })).toHaveCount(0);
  });
});
