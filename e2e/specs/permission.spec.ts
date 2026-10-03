/**
 * 権限・存在チェックのシナリオ（docs/14_e2e_test.md 4.2、#504-505）。
 *
 * 03_screen_design.md:431、F-CO-01 をカバーする。
 */

import { expect } from "@playwright/test";
import { createPost } from "../fixtures/api.ts";
import { test } from "../fixtures/auth.ts";
import { waitForAppReady } from "../fixtures/wait.ts";

test.describe("権限・存在チェック", () => {
  // #504: 他人の投稿に編集/削除メニューが出ない
  test("他人の投稿には編集・削除の操作が出ない", async ({ user: userA, secondUser }) => {
    const post = await createPost(userA.accessToken, `権限検証用投稿 ${Date.now()}`);

    // Bさん（secondUser）が他人（userA）の投稿詳細を開く
    await secondUser.page.goto(`/posts/${post.id}`);
    await waitForAppReady(secondUser.page);

    await expect(secondUser.page.getByRole("button", { name: "編集" })).toHaveCount(0);
    await expect(secondUser.page.getByRole("button", { name: "削除", exact: true })).toHaveCount(0);

    // タイムライン上のカードでも、他人の投稿には [⋯] メニューが出ない
    await secondUser.page.goto("/");
    await waitForAppReady(secondUser.page);
    const card = secondUser.page.locator(".post-card", { hasText: post.body });
    await expect(card.getByRole("button", { name: "メニュー" })).toHaveCount(0);
  });

  // #505: 存在しない投稿IDで SC-12（NotFound）
  test("存在しない投稿IDはNotFound画面になる", async ({ page }) => {
    await page.goto("/posts/999999999");
    await waitForAppReady(page);

    await expect(page.getByRole("heading", { name: "この投稿は存在しないか、削除されました" })).toBeVisible();
    await expect(page.getByRole("link", { name: "タイムラインへ戻る" })).toBeVisible();
  });

  // #505b: 存在しないユーザーIDも同様にNotFound画面になる
  test("存在しないユーザーIDはNotFound画面になる", async ({ page }) => {
    await page.goto("/users/999999999");
    await waitForAppReady(page);

    await expect(page.getByRole("heading", { name: "このユーザーは存在しません" })).toBeVisible();
  });
});
