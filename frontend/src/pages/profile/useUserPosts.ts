import { useEffect, useRef, useState } from "react";
import { fetchUserPosts } from "../../api/users";
import type { PostSummary } from "../../api/types";

type Status = "loading" | "ready" | "error";

/**
 * プロフィール（SC-05）の投稿一覧の状態管理。
 *
 * useTimeline とほぼ同形。userId が変わったら（別ユーザーのプロフィールへ移動したら）
 * 前のリクエストの応答で上書きしないよう requestIdRef で防ぐ（useTimeline のタブ切り替えと同じ理由）。
 */
export function useUserPosts(userId: number) {
  const [posts, setPosts] = useState<PostSummary[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  const loadingMoreRef = useRef(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    const requestId = ++requestIdRef.current;

    async function load() {
      setStatus("loading");
      try {
        const page = await fetchUserPosts(userId);
        if (cancelled || requestId !== requestIdRef.current) return;
        setPosts(page.items);
        setCursor(page.nextCursor);
        setHasNext(page.hasNext);
        setStatus("ready");
      } catch {
        if (!cancelled && requestId === requestIdRef.current) setStatus("error");
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [userId]);

  async function loadMore() {
    if (loadingMoreRef.current || !hasNext || !cursor) return;
    loadingMoreRef.current = true;
    setIsLoadingMore(true);
    setLoadMoreFailed(false);
    try {
      const page = await fetchUserPosts(userId, cursor);
      setPosts((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch {
      setLoadMoreFailed(true);
    } finally {
      loadingMoreRef.current = false;
      setIsLoadingMore(false);
    }
  }

  function replacePost(post: PostSummary) {
    setPosts((prev) => prev.map((p) => (p.id === post.id ? post : p)));
  }

  function removePost(postId: number) {
    setPosts((prev) => prev.filter((p) => p.id !== postId));
  }

  return {
    posts,
    status,
    hasNext,
    isLoadingMore,
    loadMoreFailed,
    loadMore,
    replacePost,
    removePost,
  };
}
