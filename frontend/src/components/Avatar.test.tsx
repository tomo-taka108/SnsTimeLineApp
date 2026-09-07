import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { UserSummary } from "../api/types";
import { Avatar } from "./Avatar";

/**
 * {@link Avatar} のテスト（docs/11_test_design.md 24.3章、ケース #434〜#437）。
 *
 * 画像が未設定なら表示名のイニシャルを出す（docs/03_screen_design.md 5章）。
 */
function user(overrides: Partial<UserSummary> = {}): UserSummary {
  return { id: 1, username: "taro", displayName: "たろう", avatarUrl: null, ...overrides };
}

describe("Avatar", () => {
  it("#434 avatarUrl があれば img を出し、オリジンを補完する", () => {
    render(<Avatar user={user({ avatarUrl: "/api/v1/files/1" })} />);

    const img = screen.getByRole("presentation", { hidden: true }) as HTMLImageElement;
    expect(img.tagName).toBe("IMG");
    expect(img.src).toBe("http://localhost:8080/api/v1/files/1");
  });

  it("#435 avatarUrl が null なら表示名の1文字目を出す", () => {
    render(<Avatar user={user({ displayName: "たろう" })} />);

    expect(screen.getByText("た")).toBeInTheDocument();
  });

  /** #436 コードポイント単位で取り出すため、絵文字が壊れない（"😀"[0] だと文字化けする） */
  it("#436 表示名が絵文字で始まっても壊れず1文字として出る", () => {
    render(<Avatar user={user({ displayName: "😀たろう" })} />);

    expect(screen.getByText("😀")).toBeInTheDocument();
  });

  it("#437 size に応じてクラスが変わる", () => {
    const { container } = render(<Avatar user={user()} size="sm" />);

    expect(container.querySelector(".avatar-sm")).not.toBeNull();
  });
});
