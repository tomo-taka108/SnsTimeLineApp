import { useEffect, useRef, useState } from "react";
import { fetchComments } from "../../api/comments";
import type { Comment } from "../../api/types";

type Status = "loading" | "ready" | "error";

/**
 * 投稿詳細のコメント一覧の状態管理（docs/03_screen_design.md SC-04）。
 *
 * useTimeline と対称だが、コメントは古い順（昇順）表示のため新規投稿・次ページとも
 * 配列の末尾に追加する点が異なる（タイムラインは先頭挿入）。
 */
export function useComments(postId: number) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  const loadingMoreRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setStatus("loading");
      try {
        const page = await fetchComments(postId);
        if (cancelled) return;
        setComments(page.items);
        setCursor(page.nextCursor);
        setHasNext(page.hasNext);
        setStatus("ready");
      } catch {
        if (!cancelled) setStatus("error");
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [postId]);

  async function loadMore() {
    if (loadingMoreRef.current || !hasNext || !cursor) return;
    loadingMoreRef.current = true;
    setIsLoadingMore(true);
    setLoadMoreFailed(false);
    try {
      const page = await fetchComments(postId, cursor);
      setComments((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch {
      setLoadMoreFailed(true);
    } finally {
      loadingMoreRef.current = false;
      setIsLoadingMore(false);
    }
  }

  function appendComment(comment: Comment) {
    setComments((prev) => [...prev, comment]); // 古い順表示なので新規コメントは末尾
  }

  function removeComment(commentId: number) {
    setComments((prev) => prev.filter((c) => c.id !== commentId));
  }

  function replaceComment(comment: Comment) {
    setComments((prev) => prev.map((c) => (c.id === comment.id ? comment : c)));
  }

  return {
    comments,
    status,
    hasNext,
    isLoadingMore,
    loadMoreFailed,
    loadMore,
    appendComment,
    removeComment,
    replaceComment,
  };
}
