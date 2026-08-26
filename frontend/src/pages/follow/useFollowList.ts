import { useEffect, useRef, useState } from "react";
import { fetchFollowers, fetchFollowing } from "../../api/users";
import type { UserListItem } from "../../api/types";

type Status = "loading" | "ready" | "error";
export type FollowListMode = "following" | "followers";

/**
 * SC-08（フォロー中一覧）/ SC-09（フォロワー一覧）の状態管理。
 *
 * useTimeline / useUserPosts と同形。mode によって呼ぶAPIを切り替える。
 */
export function useFollowList(userId: number, mode: FollowListMode) {
  const [users, setUsers] = useState<UserListItem[]>([]);
  const [status, setStatus] = useState<Status>("loading");
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [loadMoreFailed, setLoadMoreFailed] = useState(false);
  const loadingMoreRef = useRef(false);
  const requestIdRef = useRef(0);

  const fetchPage = mode === "following" ? fetchFollowing : fetchFollowers;

  useEffect(() => {
    let cancelled = false;
    const requestId = ++requestIdRef.current;

    async function load() {
      setStatus("loading");
      try {
        const page = await fetchPage(userId);
        if (cancelled || requestId !== requestIdRef.current) return;
        setUsers(page.items);
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, mode]);

  async function loadMore() {
    if (loadingMoreRef.current || !hasNext || !cursor) return;
    loadingMoreRef.current = true;
    setIsLoadingMore(true);
    setLoadMoreFailed(false);
    try {
      const page = await fetchPage(userId, cursor);
      setUsers((prev) => [...prev, ...page.items]);
      setCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch {
      setLoadMoreFailed(true);
    } finally {
      loadingMoreRef.current = false;
      setIsLoadingMore(false);
    }
  }

  function replaceUser(updated: UserListItem) {
    setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
  }

  return { users, status, hasNext, isLoadingMore, loadMoreFailed, loadMore, replaceUser };
}
