/**
 * タイムラインのシナリオ（docs/14_e2e_test.md 4.2、#490-494）。
 *
 * 無限スクロール（rootMargin）は 11_test_design.md 23.8 #10 が「E2Eでしか検証できない」
 * と明言している、本PRの主目的。タブのURL保持は 03_screen_design.md:318 の要求。
 */

import { expect } from "@playwright/test";
import { createPosts } from "../fixtures/api.ts";
import { test } from "../fixtures/auth.ts";
import { waitForAppReady } from "../fixtures/wait.ts";

test.describe("タイムライン", () => {
  // #490-492: 無限スクロール。1ページ(20件)を超える投稿がある状態で、
  // 下端200px手前（useInfiniteScroll の rootMargin）で追加読み込みが発火し、
  // ページ跨ぎで重複なく表示される（D-33のタイブレーカー検証はバックエンド側で別途実施済み）
  test("無限スクロールで追加読み込みが発火する", async ({ page, user }) => {
    // 1ページ(20件)を超える25件を作る。最初の投稿が一番下に表示される(新しい順)
    await createPosts(user.accessToken, 25, "無限スクロール検証用");

    // タイムライン(/?tab=all)は全ユーザーの投稿が混ざるため、--repeat-each等の
    // 複数回実行で前回分の投稿と重複判定が干渉する(実機検証で確認済み)。
    // 自分のプロフィール(/users/:id)の投稿一覧は自分の投稿だけなので、
    // このテストが作った25件だけを確実に対象にできる。
    // useUserPosts も同じ useInfiniteScroll を使うため、検証対象は変わらない
    await page.goto(`/users/${user.userId}`);
    await waitForAppReady(page);

    // waitForAppReady は認証復元のスピナー消滅までしか見ない。タイムライン自体の
    // データ取得はその後の非同期処理なので、最初のカードが描画されるまで明示的に待つ
    await page.locator(".post-card").first().waitFor();

    const initialCount = await page.locator(".post-card").count();
    expect(initialCount).toBeGreaterThanOrEqual(20);
    expect(initialCount).toBeLessThan(25);

    // 2ページ目のAPI呼び出し(cursorパラメータ付き)が実際に発火したことを、
    // レスポンスイベントのリスナーで検知する。件数のpollだけに頼ると、
    // 6ワーカー並列などの高負荷下でIntersectionObserverコールバックの発火が
    // 描画フレームに間に合わず、タイムアウトまで一度も交差が検知されないことが
    // 実機検証で判明した(Chrome DevTools MCPでの単体操作では常に成功することも
    // 確認済み。実装ではなく並列負荷下のタイミングが原因)。
    //
    // mouse.wheel は現在位置からの相対移動のため、カード20件分の高さを
    // 動かすには複数回必要で、回数とピクセル数の見積りがハードコードになる。
    // window.scrollTo(0, document.body.scrollHeight) で毎回ページ最下部へ
    // 直接送る方が、ページの実際の高さに追従でき確実（Chrome DevTools MCPの
    // 単体検証でも scrollTo 方式のみ確実に効いた）
    let loadMoreFired = false;
    page.on("response", (res) => {
      if (res.url().includes("/posts") && res.url().includes("cursor=")) loadMoreFired = true;
    });

    await expect
      .poll(
        async () => {
          if (!loadMoreFired) {
            await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
          }
          return loadMoreFired;
        },
        { timeout: 20000 },
      )
      .toBe(true);

    await expect
      .poll(async () => page.locator(".post-card").count(), { timeout: 5000 })
      .toBeGreaterThan(initialCount);

    // 重複がないことを確認する(D-33のタイブレーカーが壊れるとページ跨ぎで同じ投稿が
    // 再登場する。今回作った投稿は本文が全て異なる連番のため、本文の重複有無で見る)
    const bodies = await page.locator(".post-card .post-text").allTextContents();
    expect(new Set(bodies).size).toBe(bodies.length);
  });

  // #493-494: タブ切替で ?tab=following がURLに乗る→リロードで保持→ブラウザバックで戻る
  test("タブの状態がURLクエリで保持される", async ({ page }) => {
    await page.goto("/");
    await waitForAppReady(page);

    // 既定は「すべて」タブ
    await expect(page.getByRole("tab", { name: "すべて" })).toHaveAttribute("aria-selected", "true");

    // #493: 「フォロー中」タブへ切り替えるとURLクエリに乗る
    await page.getByRole("tab", { name: "フォロー中" }).click();
    await expect(page).toHaveURL(/\?tab=following$/);
    await expect(page.getByRole("tab", { name: "フォロー中" })).toHaveAttribute("aria-selected", "true");

    // #493: リロードしても保持される
    await page.reload();
    await waitForAppReady(page);
    await expect(page).toHaveURL(/\?tab=following$/);
    await expect(page.getByRole("tab", { name: "フォロー中" })).toHaveAttribute("aria-selected", "true");

    // #494: ブラウザバックで「すべて」タブに戻る
    await page.goBack();
    await expect(page).toHaveURL("/");
    await expect(page.getByRole("tab", { name: "すべて" })).toHaveAttribute("aria-selected", "true");
  });
});
