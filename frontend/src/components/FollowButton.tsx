import type { UserListItem, UserProfile } from "../api/types";
import { useFollow } from "../hooks/useFollow";

type Followable = UserProfile | UserListItem;

type Props<T extends Followable> = {
  user: T;
  onChange: (updated: T) => void;
  /** SC-05（プロフィール）は通常サイズ、SC-08/09（一覧の行内）は sm（mockup/common.css .btn-sm） */
  size?: "md" | "sm";
};

/**
 * フォロー / フォロー中トグルボタン（docs/03_screen_design.md SC-05、mockup/mock.js の
 * `[data-follow]` ハンドラに対応）。
 *
 * フォロー中はホバーで「フォロー解除」（赤）に変わる。CSS の `.btn-following:hover` が
 * `.label-following` / `.label-unfollow` の出し分けを行うため、両方のラベルを常にDOMに置く。
 *
 * **確認モーダルは出さない**（誤操作しても再フォローで復帰できるため、SC-05）。
 */
export function FollowButton<T extends Followable>({ user, onChange, size = "md" }: Props<T>) {
  const { toggle, isSubmitting } = useFollow(user, onChange);
  const sizeClass = size === "sm" ? " btn-sm" : "";

  if (user.isFollowing) {
    return (
      <button
        type="button"
        className={`btn btn-outline btn-following${sizeClass}`}
        disabled={isSubmitting}
        aria-pressed="true"
        onClick={(event) => {
          event.stopPropagation();
          void toggle();
        }}
      >
        <span className="label-following">フォロー中</span>
        <span className="label-unfollow">フォロー解除</span>
      </button>
    );
  }

  return (
    <button
      type="button"
      className={`btn btn-primary${sizeClass}`}
      disabled={isSubmitting}
      aria-pressed="false"
      onClick={(event) => {
        event.stopPropagation();
        void toggle();
      }}
    >
      フォロー
    </button>
  );
}
