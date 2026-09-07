import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Pagination } from "./Pagination";

/**
 * {@link Pagination} のテスト（docs/11_test_design.md 24.3章、ケース #447〜#450）。
 *
 * SC-07（ユーザー検索）でのみ使う。ページ番号の並びを作る buildPages は非公開のため、
 * <b>描画結果を通して検証する</b>（テストのために export を足さない）。
 */

/** 描画されているページ番号ボタンと区切り記号を、左から順に並べて返す */
function renderedItems(container: HTMLElement): string[] {
  return Array.from(container.querySelectorAll(".pagination > *"))
    .map((el) => el.textContent?.trim() ?? "")
    .filter((text) => text !== "前へ" && text !== "次へ");
}

describe("Pagination", () => {
  it("#447 1ページ以下なら何も描画しない", () => {
    const { container } = render(<Pagination page={0} totalPages={1} onChange={vi.fn()} />);

    expect(container.querySelector(".pagination")).toBeNull();
  });

  it("#448 先頭ページでは「前へ」、最終ページでは「次へ」が無効になる", () => {
    const { rerender } = render(<Pagination page={0} totalPages={5} onChange={vi.fn()} />);
    expect(screen.getByRole("button", { name: "前へ" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "次へ" })).toBeEnabled();

    rerender(<Pagination page={4} totalPages={5} onChange={vi.fn()} />);
    expect(screen.getByRole("button", { name: "前へ" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "次へ" })).toBeDisabled();
  });

  /** #449 現在5ページ目（0始まりなので表示は6）・全20ページで「1 … 5 6 7 … 20」になる */
  it("#449 離れたページの間に区切り（…）が入る", () => {
    const { container } = render(<Pagination page={5} totalPages={20} onChange={vi.fn()} />);

    expect(renderedItems(container)).toEqual(["1", "…", "5", "6", "7", "…", "20"]);
  });

  it("#449b 全ページが近ければ区切りは入らない", () => {
    const { container } = render(<Pagination page={1} totalPages={3} onChange={vi.fn()} />);

    expect(renderedItems(container)).toEqual(["1", "2", "3"]);
  });

  it("#450 現在ページのボタンに aria-current=page が付く", () => {
    render(<Pagination page={2} totalPages={5} onChange={vi.fn()} />);

    // 0始まりなので表示は「3」
    expect(screen.getByRole("button", { name: "3" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "2" })).not.toHaveAttribute("aria-current");
  });
});
