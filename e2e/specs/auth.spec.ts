/**
 * 認証シナリオ（docs/14_e2e_test.md 4.2、#479-484）。
 *
 * F-AU-01/02/03/04（新規登録・ログイン・ログアウト・ログイン状態の維持）、
 * SC-01/SC-02 をカバーする。
 */

import { expect, test } from "@playwright/test";
import { E2E_PASSWORD, signup, uniqueEmail } from "../fixtures/api.ts";
import { waitForAppReady } from "../fixtures/wait.ts";

test.describe("認証", () => {
  // #479: 新規登録→自動ログイン→リロードしても維持→ログアウト→保護ページへ直接アクセスで /login へ
  test("新規登録からログアウトまでの一連の流れ", async ({ page }, testInfo) => {
    const email = uniqueEmail("signup", testInfo.parallelIndex);
    const username = `e2esignup${testInfo.parallelIndex}${Date.now().toString(36)}`.slice(0, 30);

    await page.goto("/signup");
    await page.getByLabel("メールアドレス").fill(email);
    await page.getByLabel("ユーザー名").fill(username);
    await page.getByLabel("表示名").fill("E2E新規登録太郎");
    await page.getByLabel("パスワード", { exact: true }).fill(E2E_PASSWORD);
    await page.getByLabel("パスワード（確認）").fill(E2E_PASSWORD);
    await page.getByRole("button", { name: "登録する" }).click();

    // #479: 登録成功で自動的にログイン状態になり、タイムラインへ遷移する（F-AU-01）
    await expect(page).toHaveURL("/");
    await waitForAppReady(page);
    await expect(page.getByRole("tab", { name: "すべて" })).toBeVisible();

    // #479: リロードしてもログイン状態が維持される（F-AU-04）
    await page.reload();
    await waitForAppReady(page);
    await expect(page).toHaveURL("/");
    await expect(page.getByRole("tab", { name: "すべて" })).toBeVisible();

    // #479: ログアウトすると /login へ遷移する（F-AU-03）
    await page.getByRole("button", { name: "アカウントメニュー" }).click();
    await page.getByRole("menuitem", { name: "ログアウト" }).click();
    await expect(page).toHaveURL("/login");

    // #479: ログアウト後に保護ページへ直接アクセスすると /login へ送られる（F-CO-02）
    await page.goto("/");
    await expect(page).toHaveURL("/login");
  });

  // #480-483: 既存ユーザーでログインできる
  test("既存ユーザーでログインできる", async ({ page }, testInfo) => {
    // 先にAPIでユーザーを作っておき、ログイン画面はログイン操作だけに専念させる
    const { email } = await signup("login", testInfo.parallelIndex);

    await page.goto("/login");
    await page.getByLabel("メールアドレス").fill(email);
    await page.getByLabel("パスワード").fill(E2E_PASSWORD);
    await page.getByRole("button", { name: "ログイン" }).click();

    await expect(page).toHaveURL("/");
  });

  // #484: ログイン失敗時、フォーム上部に表示される（フィールド下ではない。03章:716 アカウント列挙対策）
  test("ログイン失敗時はフォーム上部にエラーが出る", async ({ page }, testInfo) => {
    const { email } = await signup("wrongpw", testInfo.parallelIndex);

    await page.goto("/login");
    await page.getByLabel("メールアドレス").fill(email);
    await page.getByLabel("パスワード").fill("WrongPassword1!");
    await page.getByRole("button", { name: "ログイン" }).click();

    // #484: フォーム上部（role=alert）に出る。フィールド単位のエラーにはしない
    const formAlert = page.locator(".form-alert[role='alert']");
    await expect(formAlert).toBeVisible();
    await expect(formAlert).toContainText("メールアドレスまたはパスワードが正しくありません");

    // ログイン画面にとどまる（遷移しない）
    await expect(page).toHaveURL("/login");
  });
});
