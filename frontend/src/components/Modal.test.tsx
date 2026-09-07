import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ConfirmModal } from "./ConfirmModal";
import { Modal } from "./Modal";

/**
 * {@link Modal} / {@link ConfirmModal} のテスト
 * （docs/11_test_design.md 24.3章、ケース #441〜#446）。
 *
 * 背景クリックと Escape で閉じる。開いている間は背面のスクロールを止める
 * （docs/03_screen_design.md 5章）。
 */
describe("Modal", () => {
  it("#441 isOpen が false なら何も描画しない", () => {
    render(
      <Modal isOpen={false} onClose={vi.fn()}>
        <p>中身</p>
      </Modal>,
    );

    expect(screen.queryByText("中身")).not.toBeInTheDocument();
  });

  it("#442 Escapeキーで onClose が呼ばれる", () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose}>
        <p>中身</p>
      </Modal>,
    );

    fireEvent.keyDown(document, { key: "Escape" });

    expect(onClose).toHaveBeenCalledOnce();
  });

  it("#443 背景（backdrop）をクリックすると onClose が呼ばれる", () => {
    const onClose = vi.fn();
    const { container } = render(
      <Modal isOpen onClose={onClose}>
        <p>中身</p>
      </Modal>,
    );

    fireEvent.click(container.querySelector(".modal-backdrop")!);

    expect(onClose).toHaveBeenCalledOnce();
  });

  /** #444 中身のクリックで閉じてしまうと、テキスト選択などの操作で誤って閉じる */
  it("#444 中身をクリックしても onClose は呼ばれない", () => {
    const onClose = vi.fn();
    render(
      <Modal isOpen onClose={onClose}>
        <p>中身</p>
      </Modal>,
    );

    fireEvent.click(screen.getByText("中身"));

    expect(onClose).not.toHaveBeenCalled();
  });

  it("#445 開いている間は body のスクロールが止まり、閉じると元に戻る", () => {
    const { rerender } = render(
      <Modal isOpen onClose={vi.fn()}>
        <p>中身</p>
      </Modal>,
    );
    expect(document.body.style.overflow).toBe("hidden");

    rerender(
      <Modal isOpen={false} onClose={vi.fn()}>
        <p>中身</p>
      </Modal>,
    );
    expect(document.body.style.overflow).not.toBe("hidden");
  });
});

describe("ConfirmModal", () => {
  it("#446 isDanger なら確認ボタンが赤（btn-danger）になる", () => {
    const { rerender } = render(
      <ConfirmModal
        isOpen
        title="投稿を削除しますか？"
        message="この操作は取り消せません"
        confirmLabel="削除する"
        isDanger
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: "削除する" })).toHaveClass("btn-danger");

    rerender(
      <ConfirmModal
        isOpen
        title="投稿を破棄しますか？"
        message="編集内容が失われます"
        confirmLabel="破棄する"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    );
    expect(screen.getByRole("button", { name: "破棄する" })).toHaveClass("btn-accent");
  });
});
