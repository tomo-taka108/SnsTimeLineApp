import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { createComment, deleteComment } from "../api/comments";
import { deletePost, fetchPost, updatePost } from "../api/posts";
import type { Comment, PostSummary } from "../api/types";
import { AppHeader } from "../components/AppHeader";
import { Avatar } from "../components/Avatar";
import { CommentForm } from "../components/CommentForm";
import { CommentItem } from "../components/CommentItem";
import { ConfirmModal } from "../components/ConfirmModal";
import { PostComposer } from "../components/PostComposer";
import { useAuth } from "../auth/useAuth";
import { useLike } from "../hooks/useLike";
import { useToast } from "../components/useToast";
import { formatAbsolute } from "../utils/datetime";
import { NotFoundPage } from "./NotFoundPage";
import { useComments } from "./postDetail/useComments";
import { useInfiniteScroll } from "./timeline/useInfiniteScroll";

/**
 * いいねボタン（.detail-actions）。post が確定してからマウントされるので、
 * useLike に null を渡さずに済む（PostCard と同じフックをここでも使う）。
 */
function DetailLikeButton({ post, onChange }: { post: PostSummary; onChange: (post: PostSummary) => void }) {
  const { toggle, isSubmitting } = useLike(post, onChange);

  return (
    <div className="detail-actions">
      <span className="action-btn action-comment">
        <span className="ico" aria-hidden="true">
          💬
        </span>
        <span>{post.commentCount}</span>
      </span>
      <button
        type="button"
        className={post.isLikedByMe ? "action-btn action-like is-liked" : "action-btn action-like"}
        disabled={isSubmitting}
        aria-label="いいね"
        aria-pressed={post.isLikedByMe}
        onClick={() => void toggle()}
      >
        <span className="ico" aria-hidden="true">
          {post.isLikedByMe ? "♥" : "♡"}
        </span>
        <span>{post.likeCount}</span>
      </button>
    </div>
  );
}

/** SC-04 投稿詳細。 */
export function PostDetailPage() {
  const { postId } = useParams<{ postId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [post, setPost] = useState<PostSummary | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "notFound">("loading");
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteCommentTarget, setDeleteCommentTarget] = useState<Comment | null>(null);
  const [isCommentDeleting, setIsCommentDeleting] = useState(false);

  const numericId = Number(postId);

  const {
    comments,
    status: commentsStatus,
    hasNext: hasNextComments,
    isLoadingMore: isLoadingMoreComments,
    loadMoreFailed: loadMoreCommentsFailed,
    loadMore: loadMoreComments,
    appendComment,
    removeComment,
  } = useComments(numericId);
  const commentSentinelRef = useInfiniteScroll(
    () => void loadMoreComments(),
    commentsStatus === "ready" && hasNextComments && !loadMoreCommentsFailed,
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
        const result = await fetchPost(numericId);
        if (cancelled) return;
        setPost(result);
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
    if (!post) return;
    try {
      const updated = await updatePost(post.id, { body });
      setPost(updated);
      setIsEditOpen(false);
      showToast("投稿を編集しました");
    } catch {
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
      throw new Error("update failed");
    }
  }

  async function handleDeleteConfirm() {
    if (!post) return;
    setIsDeleting(true);
    try {
      await deletePost(post.id);
      showToast("投稿を削除しました");
      navigate("/", { replace: true });
    } catch {
      showToast("この操作を行う権限がありません", true);
      setIsDeleting(false);
    }
  }

  async function handleCommentSubmit(body: string) {
    if (!post) return;
    try {
      const result = await createComment(post.id, { body });
      appendComment(result.comment);
      setPost((prev) => (prev ? { ...prev, commentCount: result.commentCount } : prev));
    } catch {
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
      throw new Error("comment create failed");
    }
  }

  async function handleCommentDeleteConfirm() {
    if (!deleteCommentTarget) return;
    setIsCommentDeleting(true);
    try {
      const result = await deleteComment(deleteCommentTarget.id);
      removeComment(deleteCommentTarget.id);
      setPost((prev) => (prev ? { ...prev, commentCount: result.commentCount } : prev));
      setDeleteCommentTarget(null);
      showToast("コメントを削除しました");
    } catch {
      showToast("通信に失敗しました。時間をおいて再度お試しください", true);
    } finally {
      setIsCommentDeleting(false);
    }
  }

  if (status === "notFound") {
    return <NotFoundPage message="この投稿は存在しないか、削除されました" />;
  }

  const isMine = post !== null && user !== null && post.author.id === user.id;

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="back-bar">
          <button className="btn btn-outline btn-sm" type="button" onClick={() => navigate(-1)}>
            ← 戻る
          </button>
          <span className="title">投稿</span>
        </div>

        {status === "loading" && (
          <div className="skeleton-card">
            <div className="sk sk-avatar" />
            <div style={{ flex: 1 }}>
              <div className="sk sk-line sk-w-30" />
              <div className="sk sk-line sk-w-100" />
              <div className="sk sk-line sk-w-60" />
            </div>
          </div>
        )}

        {status === "ready" && post && (
          <article className="post-card is-detail">
            <div className="detail-post">
              <div className="detail-head">
                <Avatar user={post.author} size="lg" />
                <div>
                  <div className="name">{post.author.displayName}</div>
                  <div className="handle">@{post.author.username}</div>
                </div>
                {isMine && (
                  <div className="spacer" style={{ marginLeft: "auto", display: "flex", gap: "8px" }}>
                    <button className="btn btn-outline btn-sm" type="button" onClick={() => setIsEditOpen(true)}>
                      編集
                    </button>
                    <button className="btn btn-danger btn-sm" type="button" onClick={() => setIsDeleteOpen(true)}>
                      削除
                    </button>
                  </div>
                )}
              </div>

              <p className="detail-text">{post.body}</p>

              <div className="detail-time">
                {formatAbsolute(post.createdAt)}
                {post.editedAt && <span className="edited">・編集済み</span>}
              </div>

              <div className="detail-stats">
                <span>
                  <strong>{post.likeCount}</strong> いいね
                </span>
                <span>
                  <strong>{post.commentCount}</strong> コメント
                </span>
              </div>

              <DetailLikeButton post={post} onChange={setPost} />
            </div>

            <CommentForm onSubmit={handleCommentSubmit} />

            <div className="section-bar">コメント（古い順）</div>

            {commentsStatus === "loading" && (
              <div className="skeleton-card">
                <div className="sk sk-avatar" />
                <div style={{ flex: 1 }}>
                  <div className="sk sk-line sk-w-30" />
                  <div className="sk sk-line sk-w-100" />
                </div>
              </div>
            )}

            {commentsStatus === "error" && (
              <div className="state-error">
                <p>コメントの取得に失敗しました</p>
              </div>
            )}

            {commentsStatus === "ready" && comments.length === 0 && (
              <div className="state-block">
                <div className="state-icon" aria-hidden="true">
                  💬
                </div>
                <h3>まだコメントはありません</h3>
                <p>最初のコメントを書いてみましょう。</p>
              </div>
            )}

            {commentsStatus === "ready" &&
              comments.map((comment) => (
                <CommentItem key={comment.id} comment={comment} onDelete={setDeleteCommentTarget} />
              ))}

            {commentsStatus === "ready" && comments.length > 0 && (
              <>
                <div ref={commentSentinelRef} />
                <div className="list-foot">
                  {loadMoreCommentsFailed ? (
                    <>
                      <p>読み込みに失敗しました</p>
                      <button className="btn btn-outline btn-sm" type="button" onClick={() => void loadMoreComments()}>
                        再試行
                      </button>
                    </>
                  ) : isLoadingMoreComments ? (
                    <span className="spinner" />
                  ) : !hasNextComments ? (
                    <span>これ以上コメントはありません</span>
                  ) : null}
                </div>
              </>
            )}
          </article>
        )}
      </main>

      {post && (
        <PostComposer
          isOpen={isEditOpen}
          initialBody={post.body}
          submitLabel="保存"
          submittingLabel="保存中..."
          onSubmit={handleUpdate}
          onClose={() => setIsEditOpen(false)}
          hint="※ 画像の差し替えはできません（本文のみ編集可）"
        />
      )}

      <ConfirmModal
        isOpen={isDeleteOpen}
        title="投稿を削除しますか？"
        message="この操作は取り消せません。"
        confirmLabel="削除"
        isDanger
        isSubmitting={isDeleting}
        onConfirm={() => void handleDeleteConfirm()}
        onCancel={() => setIsDeleteOpen(false)}
      />

      <ConfirmModal
        isOpen={deleteCommentTarget !== null}
        title="コメントを削除しますか？"
        message="この操作は取り消せません。"
        confirmLabel="削除"
        isDanger
        isSubmitting={isCommentDeleting}
        onConfirm={() => void handleCommentDeleteConfirm()}
        onCancel={() => setDeleteCommentTarget(null)}
      />
    </>
  );
}
