/**
 * 投稿のCRUDシナリオ（docs/14_e2e_test.md 4.2、#485-489）。
 *
 * F-PO-01/03/04/05（作成・詳細・編集・削除）、SC-03/SC-04、MD-01/02/03 をカバーする。
 */

import { expect } from "@playwright/test";
import { test } from "../fixtures/auth.ts";

test.describe("投稿のCRUD", () => {
  // #485-489: 投稿作成（MD-01）→TL先頭に出る→詳細へ遷移→編集（MD-02）→「編集済み」表示→削除（MD-03）→TLから消える
  test("作成・編集・削除の一連の流れ", async ({ page }) => {
    const originalBody = `E2Eテスト投稿 ${Date.now()}`;
    const editedBody = `${originalBody}（編集後）`;

    await page.goto("/");

    // #485: FAB から MD-01 を開き投稿する
    await page.getByRole("button", { name: "投稿する" }).click();
    await page.getByPlaceholder("いまどうしてる？").fill(originalBody);
    await page.getByRole("button", { name: "投稿", exact: true }).click();

    // #485: TL先頭に出る
    const firstCard = page.locator(".post-card").first();
    await expect(firstCard).toContainText(originalBody);

    // #486: 詳細へ遷移する
    await firstCard.click();
    await expect(page).toHaveURL(/\/posts\/\d+$/);
    await expect(page.locator(".detail-text")).toContainText(originalBody);

    // #487-488: 編集（MD-02）→「編集済み」表示が出る
    await page.getByRole("button", { name: "編集" }).click();
    const composerTextarea = page.getByPlaceholder("いまどうしてる？");
    await composerTextarea.fill(editedBody);
    await page.getByRole("button", { name: "保存", exact: true }).click();

    await expect(page.locator(".detail-text")).toContainText(editedBody);
    await expect(page.locator(".detail-time")).toContainText("編集済み");

    // #489: 削除（MD-03）→確認モーダル→TLから消える
    // 「削除」ボタンは本体側（.detail-head 内）とモーダル側（.modal 内）の両方に存在するため、
    // モーダル側は .modal でスコープして区別する（Modal は isOpen のときだけDOMに1つ存在する）
    await page.getByRole("button", { name: "削除", exact: true }).click();
    await page.locator(".modal").getByRole("button", { name: "削除", exact: true }).click();

    await expect(page).toHaveURL("/");
    await expect(page.locator(".post-card", { hasText: editedBody })).toHaveCount(0);
  });
});
