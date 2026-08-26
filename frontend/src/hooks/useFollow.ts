import { useState } from "react";
import { followUser, unfollowUser } from "../api/users";
import { useToast } from "../components/useToast";

/**
 * フォロー対象が最低限持つべき形。{@code UserProfile}（SC-05）と {@code UserListItem}
 * （SC-08 / SC-09）の両方をこの形で扱えるようにする。followerCount はプロフィール画面にしか
 * 無いため省略可能にし、渡された場合だけ増減させる。
 */
type Followable = {
  id: number;
  isFollowing: boolean;
  followerCount?: number;
};

/**
 * フォローの楽観的UI更新（docs/05_api_design.md #21, #22）。
 *
 * {@code useLike} とほぼ同形（docs/03_screen_design.md SC-05:
 * 「いいねと同様に楽観的UI更新を行い、失敗時は元に戻してトースト表示する」）。
 * **確認モーダルは出さない**（誤操作しても再フォローで復帰できるため）。
 */
export function useFollow<T extends Followable>(user: T, onChange: (updated: T) => void) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { showToast } = useToast();

  async function toggle() {
    if (isSubmitting) return; // 連打防止。冪等なAPIだが多重リクエスト自体は避ける
    const wasFollowing = user.isFollowing;
    const optimistic: T = {
      ...user,
      isFollowing: !wasFollowing,
      followerCount:
        user.followerCount === undefined
          ? undefined
          : Math.max(0, user.followerCount + (wasFollowing ? -1 : 1)),
    };
    onChange(optimistic);

    setIsSubmitting(true);
    try {
      const result = wasFollowing ? await unfollowUser(user.id) : await followUser(user.id);
      onChange({
        ...optimistic,
        isFollowing: result.isFollowing,
        followerCount: user.followerCount === undefined ? undefined : result.followerCount,
      });
      showToast(result.isFollowing ? "フォローしました" : "フォローを解除しました");
    } catch {
      onChange(user); // ロールバック
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
    } finally {
      setIsSubmitting(false);
    }
  }

  return { toggle, isSubmitting };
}
