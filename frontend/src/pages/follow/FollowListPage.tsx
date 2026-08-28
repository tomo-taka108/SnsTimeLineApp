import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../api/ApiError";
import { fetchProfile } from "../../api/users";
import type { UserProfile } from "../../api/types";
import { AppHeader } from "../../components/AppHeader";
import { StateBlock } from "../../components/StateBlock";
import { UserRow } from "../../components/UserRow";
import { useToast } from "../../components/useToast";
import { NotFoundPage } from "../NotFoundPage";
import { useInfiniteScroll } from "../timeline/useInfiniteScroll";
import { useFollowList, type FollowListMode } from "./useFollowList";

type Props = {
  mode: FollowListMode;
};

/**
 * SC-08（フォロー中一覧）/ SC-09（フォロワー一覧）（docs/03_screen_design.md）。
 *
 * 見出し・使用APIだけが違う同型の画面なので1コンポーネントで兼ねる。
 */
export function FollowListPage({ mode }: Props) {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const numericId = Number(userId);

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [profileStatus, setProfileStatus] = useState<"loading" | "ready" | "notFound">("loading");

  const { users, status, hasNext, isLoadingMore, loadMoreFailed, loadMore, replaceUser } =
    useFollowList(numericId, mode);
  const sentinelRef = useInfiniteScroll(
    () => void loadMore(),
    status === "ready" && hasNext && !loadMoreFailed,
  );

  useEffect(() => {
    if (!Number.isFinite(numericId)) {
      setProfileStatus("notFound");
      return;
    }
    let cancelled = false;
    async function load() {
      try {
        const result = await fetchProfile(numericId);
        if (cancelled) return;
        setProfile(result);
        setProfileStatus("ready");
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiError && error.code === "NOT_FOUND") {
          setProfileStatus("notFound");
        } else {
          showToast("通信に失敗しました。時間をおいて再度お試しください", true);
          setProfileStatus("notFound");
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numericId]);

  if (profileStatus === "notFound") {
    return <NotFoundPage message="このユーザーは存在しません" />;
  }

  const heading = mode === "following" ? "フォロー中" : "フォロワー";
  const emptyIcon = "🫂";
  const emptyTitle = mode === "following" ? "まだ誰もフォローしていません" : "まだフォロワーがいません";
  const emptyMessage =
    mode === "following"
      ? "気になるユーザーを見つけてフォローすると、ここに表示されます。"
      : "投稿を続けると、フォロワーがここに表示されます。";

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="back-bar">
          <button className="btn btn-outline btn-sm" type="button" onClick={() => navigate(`/users/${numericId}`)}>
            ← 戻る
          </button>
          <div>
            <div className="title">{heading}</div>
            {profile && <div className="sub">@{profile.username}</div>}
          </div>
        </div>

        {status === "loading" && (
          <div className="skeleton-card">
            <div className="sk sk-avatar" />
            <div style={{ flex: 1 }}>
              <div className="sk sk-line sk-w-30" />
              <div className="sk sk-line sk-w-60" />
            </div>
          </div>
        )}

        {status === "error" && (
          <div className="state-error">
            <p>一覧の取得に失敗しました</p>
            <button className="btn btn-outline btn-sm" type="button" onClick={() => window.location.reload()}>
              再試行
            </button>
          </div>
        )}

        {status === "ready" && users.length === 0 && (
          <StateBlock icon={emptyIcon} title={emptyTitle} message={emptyMessage} />
        )}

        {status === "ready" && users.map((user) => <UserRow key={user.id} user={user} onChange={replaceUser} />)}

        {status === "ready" && users.length > 0 && (
          <>
            <div ref={sentinelRef} />
            <div className="list-foot">
              {loadMoreFailed ? (
                <>
                  <p>読み込みに失敗しました</p>
                  <button className="btn btn-outline btn-sm" type="button" onClick={() => void loadMore()}>
                    再試行
                  </button>
                </>
              ) : isLoadingMore ? (
                <span className="spinner" />
              ) : !hasNext ? (
                <span>これ以上ユーザーはいません</span>
              ) : null}
            </div>
          </>
        )}
      </main>
    </>
  );
}
