import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { UserListItem } from "../api/types";
import { FollowButton } from "./FollowButton";

/**
 * {@link FollowButton} のテスト（docs/11_test_design.md 24.4章、ケース #458〜#459）。
 *
 * フォロー中はホバーで「フォロー解除」に変わるため、両方のラベルを常にDOMに置く
 * （出し分けはCSSが行う）。確認モーダルは出さない（docs/03_screen_design.md SC-05）。
 */
const toggle = vi.fn();
vi.mock("../hooks/useFollow", () => ({
  useFollow: () => ({ toggle, isSubmitting: false }),
}));

function user(isFollowing: boolean): UserListItem {
  return {
    id: 1,
    username: "taro",
    displayName: "たろう",
    avatarUrl: null,
    bio: null,
    isFollowing,
    isMe: false,
  };
}

describe("FollowButton", () => {
  it("#458 未フォローなら aria-pressed=false で「フォロー」と出る", () => {
    render(<FollowButton user={user(false)} onChange={vi.fn()} />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-pressed", "false");
    expect(button).toHaveTextContent("フォロー");
  });

  it("#458b フォロー中なら aria-pressed=true で両方のラベルを持つ", () => {
    render(<FollowButton user={user(true)} onChange={vi.fn()} />);

    const button = screen.getByRole("button");
    expect(button).toHaveAttribute("aria-pressed", "true");
    // ホバーでの出し分けはCSSが行うため、DOM上は両方存在する
    expect(screen.getByText("フォロー中")).toBeInTheDocument();
    expect(screen.getByText("フォロー解除")).toBeInTheDocument();
  });

  /**
   * #459 一覧の行はカード全体がクリック可能なため、伝播を止めないと
   * 「フォローしたのに詳細画面へ飛ぶ」ことになる。
   */
  it("#459 クリックしても親要素へイベントが伝播しない", () => {
    const onParentClick = vi.fn();
    render(
      // eslint-disable-next-line jsx-a11y/no-static-element-interactions, jsx-a11y/click-events-have-key-events
      <div onClick={onParentClick}>
        <FollowButton user={user(false)} onChange={vi.fn()} />
      </div>,
    );

    fireEvent.click(screen.getByRole("button"));

    expect(toggle).toHaveBeenCalled();
    expect(onParentClick).not.toHaveBeenCalled();
  });
});
