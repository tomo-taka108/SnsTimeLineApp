import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { PostSummary } from "../api/types";
import { useAuth } from "../auth/useAuth";
import { formatRelative } from "../utils/datetime";
import { Avatar } from "./Avatar";

type Props = {
  post: PostSummary;
  onEdit: (post: PostSummary) => void;
  onDelete: (post: PostSummary) => void;
};

/**
 * 投稿カード（docs/03_screen_design.md 5.1、mockup/mock.js postCardHtml）。
 *
 * カード全体クリックで投稿詳細へ遷移する。アバター・表示名・[⋯] はクリックを
 * 伝播させない（AppHeader.tsx のドロップダウンと同じ外側クリック＋Escapeパターン）。
 *
 * いいねボタンは今回スコープ外のため表示のみで、押しても反応しない。
 */
export function PostCard({ post, onEdit, onDelete }: Props) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const isMine = user?.id === post.author.id;

  useEffect(() => {
    if (!isMenuOpen) return;

    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false);
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setIsMenuOpen(false);
    }

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isMenuOpen]);

  return (
    <article
      className="post-card"
      onClick={() => navigate(`/posts/${post.id}`)}
      role="link"
      tabIndex={0}
      onKeyDown={(event) => {
        if (event.key === "Enter") navigate(`/posts/${post.id}`);
      }}
    >
      <span onClick={(event) => event.stopPropagation()}>
        <Avatar user={post.author} />
      </span>
      <div className="post-body-col">
        <div className="post-head">
          <span className="name" onClick={(event) => event.stopPropagation()}>
            {post.author.displayName}
          </span>
          <span className="handle">@{post.author.username}</span>
          <span className="handle">・</span>
          <span className="time">{formatRelative(post.createdAt)}</span>
          {post.editedAt && <span className="edited">・編集済み</span>}
          <span className="spacer" />
          {isMine && (
            <div className="more-menu" ref={menuRef} onClick={(event) => event.stopPropagation()}>
              <button
                className="more-btn"
                type="button"
                onClick={() => setIsMenuOpen((prev) => !prev)}
                aria-haspopup="menu"
                aria-expanded={isMenuOpen}
                aria-label="メニュー"
              >
                ⋯
              </button>
              <div className={isMenuOpen ? "dropdown is-open" : "dropdown"} role="menu">
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setIsMenuOpen(false);
                    onEdit(post);
                  }}
                >
                  <span aria-hidden="true">✏️</span> 編集
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setIsMenuOpen(false);
                    onDelete(post);
                  }}
                >
                  <span aria-hidden="true">🗑</span> 削除
                </button>
              </div>
            </div>
          )}
        </div>
        <p className="post-text is-clamped">{post.body}</p>
        <div className="post-actions">
          <span className="action-btn action-comment">
            <span className="ico" aria-hidden="true">
              💬
            </span>
            <span>{post.commentCount}</span>
          </span>
          {/* いいねは今回スコープ外。表示のみで onClick を付けない */}
          <span className={post.isLikedByMe ? "action-btn action-like is-liked" : "action-btn action-like"}>
            <span className="ico" aria-hidden="true">
              {post.isLikedByMe ? "♥" : "♡"}
            </span>
            <span>{post.likeCount}</span>
          </span>
        </div>
      </div>
    </article>
  );
}
