/**
 * 画面の4状態シナリオ（docs/14_e2e_test.md 4.2、#495-498）。
 *
 * docs/06_non_functional.md:362「一覧の4状態（ローディング / 空 / エラー / 正常）が
 * すべて実装されている」のチェックリストを、page.route でAPIを操作して検証する。
 */

import { expect } from "@playwright/test";
import { test } from "../fixtures/auth.ts";
import { waitForAppReady } from "../fixtures/wait.ts";

test.describe("タイムラインの4状態", () => {
  // #495: ローディング状態（スケルトンが出る）
  test("ローディング中はスケルトンが表示される", async ({ page }) => {
    // レスポンスを意図的に遅らせ、ローディング状態を観測できる時間を作る
    await page.route("**/api/v1/timeline*", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
    });

    await page.goto("/");
    // RequireAuth のスピナーが消えた直後、タイムライン自体のスケルトンが見えるはず
    await expect(page.locator(".skeleton-card, .sk-line").first()).toBeVisible();
  });

  // #496: 空状態（0件のときの案内文＋行動導線）
  test("投稿が0件のときは案内文とボタンが出る", async ({ page }) => {
    await page.route("**/api/v1/timeline*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ items: [], nextCursor: null, hasNext: false }),
      });
    });

    await page.goto("/");
    await waitForAppReady(page);

    await expect(page.getByText("まだ投稿がありません")).toBeVisible();
    await expect(page.getByRole("button", { name: "投稿する" }).last()).toBeVisible();
  });

  // #497: エラー状態（取得失敗時の再試行導線）＋再試行ボタンで回復する
  test("取得失敗時はエラー表示と再試行ボタンが出て、再試行で回復する", async ({ page }) => {
    let shouldFail = true;
    await page.route("**/api/v1/timeline*", async (route) => {
      if (shouldFail) {
        await route.abort("failed");
        return;
      }
      await route.continue();
    });

    await page.goto("/");
    await waitForAppReady(page);

    await expect(page.getByText("タイムラインの取得に失敗しました")).toBeVisible();
    const retryButton = page.getByRole("button", { name: "再試行" });
    await expect(retryButton).toBeVisible();

    // #497: 再試行ボタンで回復する
    shouldFail = false;
    await retryButton.click();
    await expect(page.getByText("タイムラインの取得に失敗しました")).toHaveCount(0);
  });

  // #498: 正常状態（投稿が1件以上あれば一覧が出る）
  test("投稿があれば一覧が表示される", async ({ page }) => {
    await page.route("**/api/v1/timeline*", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          items: [
            {
              id: 999999,
              author: { id: 1, username: "dummy", displayName: "ダミー", avatarUrl: null },
              body: "4状態検証用の正常系ダミー投稿",
              images: [],
              likeCount: 0,
              commentCount: 0,
              isLikedByMe: false,
              createdAt: new Date().toISOString(),
              editedAt: null,
            },
          ],
          nextCursor: null,
          hasNext: false,
        }),
      });
    });

    await page.goto("/");
    await waitForAppReady(page);

    await expect(page.locator(".post-card")).toHaveCount(1);
    await expect(page.locator(".post-card")).toContainText("4状態検証用の正常系ダミー投稿");
  });
});
