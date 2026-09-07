import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { FormField } from "./FormField";

/**
 * {@link FormField} のテスト（docs/11_test_design.md 24.3章、ケース #438〜#440）。
 *
 * エラーは該当フィールドの直下に出す（docs/03_screen_design.md 8章）。
 */
describe("FormField", () => {
  it("#438 エラーが無ければ aria-invalid は false で、hint が出る", () => {
    render(<FormField id="email" label="メールアドレス" hint="ログインに使います" />);

    const input = screen.getByLabelText("メールアドレス");
    expect(input).toHaveAttribute("aria-invalid", "false");
    expect(screen.getByText("ログインに使います")).toBeInTheDocument();
  });

  /** #439 エラー時は hint を出さない。両方出ると何を直せばよいか分かりにくい */
  it("#439 エラーがあれば role=alert で表示され、hint は消える", () => {
    render(
      <FormField
        id="email"
        label="メールアドレス"
        hint="ログインに使います"
        error="メールアドレスの形式が正しくありません"
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("メールアドレスの形式が正しくありません");
    expect(screen.queryByText("ログインに使います")).not.toBeInTheDocument();
  });

  it("#440 エラー時は aria-describedby がエラー要素を指す", () => {
    render(<FormField id="email" label="メールアドレス" error="必須です" />);

    const input = screen.getByLabelText("メールアドレス");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(input).toHaveAttribute("aria-describedby", "email-error");
    expect(document.getElementById("email-error")).toHaveTextContent("必須です");
  });
});
