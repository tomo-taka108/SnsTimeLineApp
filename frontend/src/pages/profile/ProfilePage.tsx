import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../../api/ApiError";
import { deletePost, updatePost } from "../../api/posts";
import type { PostSummary, UserProfile } from "../../api/types";
import { fetchProfile } from "../../api/users";
import { AppHeader } from "../../components/AppHeader";
import { Avatar } from "../../components/Avatar";
import { ConfirmModal } from "../../components/ConfirmModal";
import { FollowButton } from "../../components/FollowButton";
import { PostCard } from "../../components/PostCard";
import { PostCardSkeleton } from "../../components/PostCardSkeleton";
import { PostComposer } from "../../components/PostComposer";
import { useToast } from "../../components/useToast";
import { formatJoined } from "../../utils/datetime";
import { NotFoundPage } from "../NotFoundPage";
import { useInfiniteScroll } from "../timeline/useInfiniteScroll";
import { useUserPosts } from "./useUserPosts";

/**
 * SC-05 プロフィール（docs/03_screen_design.md）。
 *
 * タブは無い（投稿一覧のみ）。自分か他人かは API レスポンスの isMe で分岐する。
 */
export function ProfilePage() {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const numericId = Number(userId);

  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "notFound">("loading");
  const [editingPost, setEditingPost] = useState<PostSummary | null>(null);
  const [deletingPost, setDeletingPost] = useState<PostSummary | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const {
    posts,
    status: postsStatus,
    hasNext,
    isLoadingMore,
    loadMoreFailed,
    loadMore,
    replacePost,
    removePost,
  } = useUserPosts(numericId);
  const sentinelRef = useInfiniteScroll(
    () => void loadMore(),
    postsStatus === "ready" && hasNext && !loadMoreFailed,
  );

  useEffect(() => {
    if (!Number.isFinite(numericId)) {
      setStatus("notFound");
      return;
    }

    let cancelled = false;
    async function load() {
      setStatus("loading");
      try {
        const result = await fetchProfile(numericId);
        if (cancelled) return;
        setProfile(result);
        setStatus("ready");
      } catch (error) {
        if (cancelled) return;
        if (error instanceof ApiError && error.code === "NOT_FOUND") {
          setStatus("notFound");
        } else {
          showToast("通信に失敗しました。時間をおいて再度お試しください", true);
          setStatus("notFound");
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numericId]);

  async function handleUpdate(body: string) {
    if (!editingPost) return;
    try {
      const updated = await updatePost(editingPost.id, { body });
      replacePost(updated);
      setEditingPost(null);
      showToast("投稿を編集しました");
    } catch {
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
      throw new Error("update failed");
    }
  }

  async function handleDeleteConfirm() {
    if (!deletingPost) return;
    setIsDeleting(true);
    try {
      await deletePost(deletingPost.id);
      removePost(deletingPost.id);
      setDeletingPost(null);
      showToast("投稿を削除しました");
      setProfile((prev) => (prev ? { ...prev, postCount: Math.max(0, prev.postCount - 1) } : prev));
    } catch {
      showToast("この操作を行う権限がありません", true);
    } finally {
      setIsDeleting(false);
    }
  }

  if (status === "notFound") {
    return <NotFoundPage message="このユーザーは存在しません" />;
  }

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="back-bar">
          <button className="btn btn-outline btn-sm" type="button" onClick={() => navigate(-1)}>
            ← 戻る
          </button>
          {profile && (
            <div>
              <div className="title">{profile.displayName}</div>
              <div className="sub">{profile.postCount} 件の投稿</div>
            </div>
          )}
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

        {status === "ready" && profile && (
          <>
            <div className="profile-cover" />
            <div className="profile-head">
              <div className="profile-top">
                <Avatar user={profile} size="lg" />
                {profile.isMe ? (
                  <Link className="btn btn-outline" to="/settings/profile">
                    プロフィールを編集
                  </Link>
                ) : (
                  <FollowButton user={profile} onChange={setProfile} />
                )}
              </div>

              <h1 className="profile-name">{profile.displayName}</h1>
              <div className="profile-handle">@{profile.username}</div>

              {profile.bio && <p className="profile-bio">{profile.bio}</p>}

              <div className="profile-meta">
                <span aria-hidden="true">📅</span>
                <span>{formatJoined(profile.createdAt)}</span>
              </div>

              <div className="profile-counts">
                <Link to={`/users/${profile.id}/following`}>
                  <strong>{profile.followingCount}</strong> フォロー中
                </Link>
                <Link to={`/users/${profile.id}/followers`}>
                  <strong>{profile.followerCount}</strong> フォロワー
                </Link>
              </div>
            </div>

            <div className="section-bar">投稿　{profile.postCount}件</div>

            {postsStatus === "loading" && (
              <>
                <PostCardSkeleton />
                <PostCardSkeleton />
              </>
            )}

            {postsStatus === "error" && (
              <div className="state-error">
                <p>投稿の取得に失敗しました</p>
              </div>
            )}

            {postsStatus === "ready" && posts.length === 0 && (
              <div className="state-block">
                <div className="state-icon" aria-hidden="true">
                  ✏️
                </div>
                <h3>まだ投稿がありません</h3>
              </div>
            )}

            {postsStatus === "ready" &&
              posts.map((post) => (
                <PostCard
                  key={post.id}
                  post={post}
                  onEdit={setEditingPost}
                  onDelete={setDeletingPost}
                  onLikeChange={replacePost}
                />
              ))}

            {postsStatus === "ready" && posts.length > 0 && (
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
                    <span>これ以上投稿はありません</span>
                  ) : null}
                </div>
              </>
            )}
          </>
        )}
      </main>

      <PostComposer
        key={editingPost?.id ?? "none"}
        isOpen={editingPost !== null}
        initialBody={editingPost?.body}
        submitLabel="保存"
        submittingLabel="保存中..."
        onSubmit={handleUpdate}
        onClose={() => setEditingPost(null)}
        hint="※ 画像の差し替えはできません（本文のみ編集可）"
      />

      <ConfirmModal
        isOpen={deletingPost !== null}
        title="投稿を削除しますか？"
        message="この操作は取り消せません。"
        confirmLabel="削除"
        isDanger
        isSubmitting={isDeleting}
        onConfirm={() => void handleDeleteConfirm()}
        onCancel={() => setDeletingPost(null)}
      />
    </>
  );
}
