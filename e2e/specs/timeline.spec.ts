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
    // 「rootMarginの境界に届いていないので永遠に増えない」ケースと
    // 「増えるのを待っているだけ」のケースを区別できない
    let loadMoreFired = false;
    page.on("response", (res) => {
      if (res.url().includes("/posts") && res.url().includes("cursor=")) loadMoreFired = true;
    });

    // 最下部まで直接スクロールするだけでは発火しないことがあった。原因は、
    // ページロード直後（useInfiniteScroll の observer がまだ observe() されて
    // いないタイミング）に最下部までスクロールしてしまうと、observer 確立後に
    // 「既に交差状態」のまま固定され、状態変化が無いために新たなコールバックが
    // 発火しないこと（IntersectionObserver は状態が変化したときに通知する仕組み）
    // と判明した（Chrome DevTools MCPで新規observerを張ると isIntersecting:true に
    // なる一方、scrollTo直後は発火せず、少し待ってからscrollすると発火する実機検証
    // で確認）。毎回「現在位置と異なる中間位置」を経由してから最下部へ送ることで、
    // scrollTo(0,0)が既に0付近のときに変化なしと判定される事態を避け、
    // 確実に「非交差→交差」の状態変化を起こす
    let attempt = 0;
    await expect
      .poll(
        async () => {
          if (loadMoreFired) return true;
          attempt++;
          await page.evaluate((n) => {
            // 試行ごとに異なる中間位置へ飛ぶ（毎回0に戻すとscrollTo(0,0)が
            // 既に0付近で「変化なし」と扱われることがあるため）
            const mid = (document.body.scrollHeight / 2) + (n % 3) * 50;
            window.scrollTo(0, mid);
          }, attempt);
          await page.evaluate(() => {
            window.scrollTo(0, document.body.scrollHeight);
          });
          return loadMoreFired;
        },
        { timeout: 20000, intervals: [500] },
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
