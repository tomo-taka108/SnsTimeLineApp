import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { createPost, deletePost, updatePost } from "../../api/posts";
import type { PostSummary, TimelineTab } from "../../api/types";
import { AppHeader } from "../../components/AppHeader";
import { ConfirmModal } from "../../components/ConfirmModal";
import { PostCard } from "../../components/PostCard";
import { PostCardSkeleton } from "../../components/PostCardSkeleton";
import { PostComposer } from "../../components/PostComposer";
import { StateBlock } from "../../components/StateBlock";
import { useToast } from "../../components/useToast";
import { useInfiniteScroll } from "./useInfiniteScroll";
import { useNewPostCount } from "./useNewPostCount";
import { useTimeline } from "./useTimeline";

/**
 * SC-03 タイムライン。
 *
 * URLクエリ ?tab=all|following で状態を持つ（リロード・ブラウザバック・URL共有で保持）。
 * 不正な値は "all" にフォールバックする。
 */
export function TimelinePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get("tab");
  const tab: TimelineTab = tabParam === "following" ? "following" : "all";

  const { showToast } = useToast();
  const timeline = useTimeline(tab);
  const { posts, status, hasNext, isLoadingMore, loadMoreFailed } = timeline;

  const [isComposeOpen, setIsComposeOpen] = useState(false);
  const [editingPost, setEditingPost] = useState<PostSummary | null>(null);
  const [deletingPost, setDeletingPost] = useState<PostSummary | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const newestPostId = posts[0]?.id;
  const { count: newCount, reset: resetNewCount } = useNewPostCount(tab, newestPostId);

  const sentinelRef = useInfiniteScroll(
    () => void timeline.loadMore(),
    status === "ready" && hasNext && !loadMoreFailed,
  );

  function handleTabChange(next: TimelineTab) {
    setSearchParams({ tab: next });
  }

  async function handleShowNewPosts() {
    resetNewCount();
    await timeline.reload();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function handleCreate(body: string) {
    try {
      const post = await createPost({ body });
      timeline.prependPost(post);
      setIsComposeOpen(false);
      showToast("投稿しました");
    } catch {
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
      throw new Error("create failed");
    }
  }

  async function handleUpdate(body: string) {
    if (!editingPost) return;
    try {
      const updated = await updatePost(editingPost.id, { body });
      timeline.replacePost(updated);
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
      timeline.removePost(deletingPost.id);
      setDeletingPost(null);
      showToast("投稿を削除しました");
    } catch {
      showToast("この操作を行う権限がありません", true);
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="tabs" role="tablist">
          <button
            className={tab === "all" ? "tab is-active" : "tab"}
            type="button"
            role="tab"
            aria-selected={tab === "all"}
            onClick={() => handleTabChange("all")}
          >
            <span className="tab-label">すべて</span>
          </button>
          <button
            className={tab === "following" ? "tab is-active" : "tab"}
            type="button"
            role="tab"
            aria-selected={tab === "following"}
            onClick={() => handleTabChange("following")}
          >
            <span className="tab-label">フォロー中</span>
          </button>
        </div>

        {newCount > 0 && (
          <button className="new-posts-pill" type="button" onClick={() => void handleShowNewPosts()}>
            {newCount}件の新しい投稿
          </button>
        )}

        {status === "loading" && (
          <>
            <PostCardSkeleton />
            <PostCardSkeleton />
            <PostCardSkeleton />
          </>
        )}

        {status === "error" && (
          <div className="state-error">
            <p>タイムラインの取得に失敗しました</p>
            <button className="btn btn-outline btn-sm" type="button" onClick={() => void timeline.reload()}>
              再試行
            </button>
          </div>
        )}

        {status === "ready" && posts.length === 0 && tab === "all" && (
          <StateBlock
            icon="✏️"
            title="まだ投稿がありません"
            message="最初の投稿をしてみましょう。"
            action={
              <button className="btn btn-accent" type="button" onClick={() => setIsComposeOpen(true)}>
                投稿する
              </button>
            }
          />
        )}

        {status === "ready" && posts.length === 0 && tab === "following" && (
          <StateBlock
            icon="👀"
            title="まだ投稿がありません"
            message={"フォロー中のユーザーの投稿がここに表示されます。\n気になるユーザーをフォローしてみましょう。"}
          />
        )}

        {status === "ready" &&
          posts.map((post) => (
            <PostCard key={post.id} post={post} onEdit={setEditingPost} onDelete={setDeletingPost} />
          ))}

        {status === "ready" && posts.length > 0 && (
          <>
            <div ref={sentinelRef} />
            <div className="list-foot">
              {loadMoreFailed ? (
                <>
                  <p>読み込みに失敗しました</p>
                  <button className="btn btn-outline btn-sm" type="button" onClick={() => void timeline.loadMore()}>
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
      </main>

      <button className="fab" type="button" onClick={() => setIsComposeOpen(true)}>
        <span aria-hidden="true">✏️</span>
        <span className="fab-label">投稿する</span>
      </button>

      <PostComposer
        isOpen={isComposeOpen}
        submitLabel="投稿"
        submittingLabel="投稿中..."
        onSubmit={handleCreate}
        onClose={() => setIsComposeOpen(false)}
      />

      <PostComposer
        // 投稿ごとに再マウントし、initialBody の変更を確実に反映する
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
