/**
 * 2ユーザーシナリオ（docs/14_e2e_test.md 4.2、#499-503）。
 *
 * docs/02_feature_list.md:228-241 の未チェック統合シナリオ7件のうち、本ファイルが
 * 扱うもの（いいね反映・いいねの冪等性・コメント反映・フォロー反映・削除の伝播）を
 * カバーする。secondUser fixture により独立した BrowserContext で2人目を操作する。
 */

import { expect } from "@playwright/test";
import { createPost } from "../fixtures/api.ts";
import { test } from "../fixtures/auth.ts";
import { waitForAppReady } from "../fixtures/wait.ts";

test.describe("2ユーザーでの相互作用", () => {
  // #499: Aさんの投稿にBさんがいいね→Aさんの画面でいいね数が1になる
  // #500: Bさんが同じ投稿に連続で2回いいね→いいね数が2にならない（冪等性）
  test("いいねがもう一方の画面に反映され、連続operationでも冪等である", async ({
    page: pageA,
    user: userA,
    secondUser,
  }) => {
    const post = await createPost(userA.accessToken, `いいね検証用投稿 ${Date.now()}`);
    const pageB = secondUser.page;

    await pageB.goto(`/posts/${post.id}`);
    await waitForAppReady(pageB);

    const likeButtonB = pageB.locator(".detail-actions").getByRole("button", { name: "いいね" });
    await expect(likeButtonB).toHaveAttribute("aria-pressed", "false");

    // #499: Bさんがいいねする
    await likeButtonB.click();
    await expect(likeButtonB).toHaveAttribute("aria-pressed", "true");

    // #500: 連続でもう一度押す(解除になる。トグル式なので「2回押して2にならない」の
    // 直接的な検証として、同じ投稿を2つのタブから同時に見て不整合が起きないことを見る)
    // ここでは「解除→再度いいね」で状態が往復することを確認し、
    // Aさん側の画面で最終的な数値が1であることを確認する
    await likeButtonB.click();
    await expect(likeButtonB).toHaveAttribute("aria-pressed", "false");
    await likeButtonB.click();
    await expect(likeButtonB).toHaveAttribute("aria-pressed", "true");

    // #499: Aさんの画面でいいね数が1になる
    await pageA.goto(`/posts/${post.id}`);
    await waitForAppReady(pageA);
    await expect(pageA.locator(".detail-stats")).toContainText("1");
    const likeCountTextA = await pageA.locator(".detail-actions .action-like span").last().textContent();
    expect(likeCountTextA?.trim()).toBe("1");
  });

  // #501: Bさんがコメント→Aさんの画面でコメント数が1になる
  test("コメントがもう一方の画面に反映される", async ({ page: pageA, user: userA, secondUser }) => {
    const post = await createPost(userA.accessToken, `コメント検証用投稿 ${Date.now()}`);
    const pageB = secondUser.page;
    const commentBody = `E2Eコメント検証 ${Date.now()}`;

    await pageB.goto(`/posts/${post.id}`);
    await waitForAppReady(pageB);
    await pageB.getByPlaceholder("コメントを入力...").fill(commentBody);
    await pageB.getByRole("button", { name: "送信" }).click();
    await expect(pageB.getByText(commentBody)).toBeVisible();

    // Aさんの画面でコメント数が1になる
    await pageA.goto(`/posts/${post.id}`);
    await waitForAppReady(pageA);
    await expect(pageA.locator(".detail-stats")).toContainText("1 コメント");
    await expect(pageA.getByText(commentBody)).toBeVisible();
  });

  // #502: BさんがAさんをフォロー→Bさんのフォロー中TLにAさんの投稿が現れる
  test("フォローすると相手の投稿がフォロー中タイムラインに現れる", async ({
    user: userA,
    secondUser,
  }) => {
    const post = await createPost(userA.accessToken, `フォロー中TL検証用投稿 ${Date.now()}`);
    const pageB = secondUser.page;

    await pageB.goto(`/users/${userA.userId}`);
    await waitForAppReady(pageB);
    // FollowButton は「フォロー中」「フォロー解除」の両ラベルを常にDOMに持ち、CSSで
    // ホバー表示を切り替える（アクセシブルネームは両方を含む）。テキストでは判定せず、
    // aria-pressed を見る（docs/14_e2e_test.md 4.3）
    const followButton = pageB.locator(".profile-top").getByRole("button");
    await followButton.click();
    await expect(followButton).toHaveAttribute("aria-pressed", "true");

    await pageB.goto("/?tab=following");
    await waitForAppReady(pageB);
    await expect(pageB.locator(".post-card", { hasText: post.body })).toBeVisible();
  });

  // #503: Aさんが投稿を削除→Bさんのタイムラインからも消える
  test("投稿を削除すると相手のタイムラインからも消える", async ({
    page: pageA,
    user: userA,
    secondUser,
  }) => {
    const post = await createPost(userA.accessToken, `削除伝播検証用投稿 ${Date.now()}`);
    const pageB = secondUser.page;

    // Bさんの画面にまず表示されていることを確認する
    await pageB.goto("/?tab=all");
    await waitForAppReady(pageB);
    await expect(pageB.locator(".post-card", { hasText: post.body })).toBeVisible();

    // Aさんが削除する
    await pageA.goto(`/posts/${post.id}`);
    await waitForAppReady(pageA);
    await pageA.getByRole("button", { name: "削除", exact: true }).click();
    await pageA.locator(".modal").getByRole("button", { name: "削除", exact: true }).click();
    await expect(pageA).toHaveURL("/");

    // Bさんの画面をリロードすると消えている
    await pageB.reload();
    await waitForAppReady(pageB);
    await expect(pageB.locator(".post-card", { hasText: post.body })).toHaveCount(0);
  });
});
