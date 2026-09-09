import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CommentForm } from "./CommentForm";

/**
 * {@link CommentForm} のテスト（docs/11_test_design.md 24.4章、ケース #451〜#457）。
 *
 * 新規投稿と編集の両方で使う。<b>送信後にクリアするかどうかが両者で異なる</b>
 * （編集時は呼び出し側が編集モードを閉じるため、こちらではクリアしない）。
 *
 * useAuth を使うため、ログイン済みユーザーを返すようモックする（未ログインだと null を返す）。
 */
vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: 1, username: "taro", displayName: "たろう", avatarUrl: null },
  }),
}));

function typeInto(value: string) {
  fireEvent.change(screen.getByRole("textbox"), { target: { value } });
}

describe("CommentForm", () => {
  it("#451 空のままでは送信ボタンが押せない", () => {
    render(<CommentForm onSubmit={vi.fn()} />);

    expect(screen.getByRole("button", { name: "送信" })).toBeDisabled();
  });

  it("#452 261文字を超えると文字数カウンタが警告色になる", () => {
    const { container } = render(<CommentForm onSubmit={vi.fn()} />);

    typeInto("あ".repeat(261));

    expect(container.querySelector(".char-counter")).toHaveClass("is-warn");
  });

  /** #453 上限を超えたら送信させない。サーバーで400になる前に気づかせる */
  it("#453 281文字だとカウンタが超過色になり、送信できない", () => {
    const { container } = render(<CommentForm onSubmit={vi.fn()} />);

    typeInto("あ".repeat(281));

    expect(container.querySelector(".char-counter")).toHaveClass("is-over");
    expect(screen.getByRole("button", { name: "送信" })).toBeDisabled();
  });

  it("#454 送信時はトリム済みの本文が渡る", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<CommentForm onSubmit={onSubmit} />);

    typeInto("  こんにちは  ");
    fireEvent.click(screen.getByRole("button", { name: "送信" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("こんにちは"));
  });

  /** #455 新規投稿はクリアする。しないと同じ内容を二重投稿しやすい */
  it("#455 新規投稿は送信成功後に入力欄がクリアされる", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<CommentForm onSubmit={onSubmit} />);

    typeInto("こんにちは");
    fireEvent.click(screen.getByRole("button", { name: "送信" }));

    await waitFor(() => expect(screen.getByRole("textbox")).toHaveValue(""));
  });

  /** #456 編集時（onCancel あり）はクリアしない。呼び出し側が編集モードを閉じる */
  it("#456 編集時は送信成功後もクリアされない", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <CommentForm
        onSubmit={onSubmit}
        initialBody="編集前の本文"
        submitLabel="保存"
        onCancel={vi.fn()}
      />,
    );

    typeInto("編集後の本文");
    fireEvent.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith("編集後の本文"));
    expect(screen.getByRole("textbox")).toHaveValue("編集後の本文");
  });

  it("#457 送信が失敗しても入力は保持される", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("network"));
    render(<CommentForm onSubmit={onSubmit} />);

    typeInto("消えないでほしい");
    fireEvent.click(screen.getByRole("button", { name: "送信" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(screen.getByRole("textbox")).toHaveValue("消えないでほしい");
  });
});
