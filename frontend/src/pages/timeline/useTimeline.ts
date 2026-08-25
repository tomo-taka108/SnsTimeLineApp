import { useEffect, useRef, useState } from "react";
import { fetchTimeline } from "../../api/posts";
import type { PostSummary, TimelineTab } from "../../api/types";

type Status = "loading" | "ready" | "error";

/**
 * タイムラインの状態を一元管理する（docs/03_screen_design.md SC-03）。
 *
 * 投稿の挿入・置換・除去がすべて同じ posts 配列を触るため、ページに
 * useState を並べずここに集約する。
 */
export function useTimeline(tab: TimelineTab) {
  const [posts, setPosts] = useState<PostSummary[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  const loadingMoreRef = useRef(false);
  // タブが切り替わった後に、前のタブの応答で状態を上書きしないためのガード
  const requestIdRef = useRef(0);

  async function loadFromHead() {
    const requestId = ++requestIdRef.current;
    setStatus("loading");
    try {
      const page = await fetchTimeline(tab);
      if (requestId !== requestIdRef.current) return;
      setPosts(page.items);
      setCursor(page.nextCursor);
      setHasNext(page.hasNext);
      setStatus("ready");
    } catch {
      if (requestId === requestIdRef.current) setStatus("error");
    }
  }

  // タブが変わったら全リセット（カーソルも破棄。タブごとのキャッシュは持たない。
  // docs/03_screen_design.md SC-03「タブ切り替え時はリストをリセットして先頭から取得し直す」）
  useEffect(() => {
    void loadFromHead();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab]);

  async function loadMore() {
    if (loadingMoreRef.current || !hasNext || !cursor) return;
    loadingMoreRef.current = true;
    setIsLoadingMore(true);
    setLoadMoreFailed(false);
    try {
      const page = await fetchTimeline(tab, cursor);
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

  function prependPost(post: PostSummary) {
    setPosts((prev) => [post, ...prev]);
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
    reload: loadFromHead,
    loadMore,
    prependPost,
    replacePost,
    removePost,
  };
}
