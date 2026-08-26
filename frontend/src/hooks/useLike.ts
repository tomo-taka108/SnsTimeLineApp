import { useState } from "react";
import { likePost, unlikePost } from "../api/posts";
import type { PostSummary } from "../api/types";
import { useToast } from "../components/useToast";

/**
 * いいねの楽観的UI更新（docs/05_api_design.md #14, #15）。
 *
 * タイムライン（PostCard）・投稿詳細（PostDetailPage）の両方で共有する。
 * クリック直後に見た目を先に更新し、レスポンスで実値に置き換える。失敗したら元に戻す
 * （mockup/mock.js のトグル体験と同じ）。
 */
export function useLike(post: PostSummary, onChange: (updated: PostSummary) => void) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { showToast } = useToast();

  async function toggle() {
    if (isSubmitting) return; // 連打防止。冪等なAPIだが多重リクエスト自体は避ける
    const wasLiked = post.isLikedByMe;
    const optimistic: PostSummary = {
      ...post,
      isLikedByMe: !wasLiked,
      likeCount: Math.max(0, post.likeCount + (wasLiked ? -1 : 1)),
    };
    onChange(optimistic);

    setIsSubmitting(true);
    try {
      const result = wasLiked ? await unlikePost(post.id) : await likePost(post.id);
      onChange({ ...optimistic, likeCount: result.likeCount, isLikedByMe: result.isLikedByMe });
    } catch {
      onChange(post); // ロールバック
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
    } finally {
      setIsSubmitting(false);
    }
  }

  return { toggle, isSubmitting };
}
