import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import type { Comment } from "../api/types";
import { formatRelative } from "../utils/datetime";
import { Avatar } from "./Avatar";
import { CommentForm } from "./CommentForm";

type Props = {
  comment: Comment;
  isEditing: boolean;
  onEditStart: (comment: Comment) => void;
  onEditSubmit: (body: string) => Promise<void>;
  onEditCancel: () => void;
  onDelete: (comment: Comment) => void;
};

/**
 * コメント1件（SC-04、mockup/mock.js commentHtml）。
 *
 * [⋯] メニューは自分のコメントのみ。編集（F-CM-03、D-51 でMVPへ前倒し）と削除の2項目。
 * 外側クリック＋Escapeで閉じるパターンは PostCard.tsx と同じ（D-35、2箇所目は重複を許容）。
 * 編集中は本文の代わりに CommentForm をインライン表示する（モーダルは使わない）。
 */
export function CommentItem({ comment, isEditing, onEditStart, onEditSubmit, onEditCancel, onDelete }: Props) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

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
    <div className="comment-item">
      <Link to={`/users/${comment.author.id}`}>
        <Avatar user={comment.author} size="sm" />
      </Link>
      <div className="comment-main">
        <div className="post-head">
          <Link className="name" to={`/users/${comment.author.id}`}>
            {comment.author.displayName}
          </Link>
          <span className="handle">@{comment.author.username}</span>
          <span className="handle">・</span>
          <span className="time">{formatRelative(comment.createdAt)}</span>
          {comment.editedAt && <span className="edited">・編集済み</span>}
          <span className="spacer" />
          {comment.isMine && !isEditing && (
            <div className="more-menu" ref={menuRef}>
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
                    onEditStart(comment);
                  }}
                >
                  <span aria-hidden="true">✏️</span> 編集
                </button>
                <button
                  type="button"
                  role="menuitem"
                  onClick={() => {
                    setIsMenuOpen(false);
                    onDelete(comment);
                  }}
                >
                  <span aria-hidden="true">🗑</span> 削除
                </button>
              </div>
            </div>
          )}
        </div>
        {isEditing ? (
          <CommentForm
            initialBody={comment.body}
            submitLabel="保存"
            onSubmit={onEditSubmit}
            onCancel={onEditCancel}
            autoFocus
          />
        ) : (
          <p className="comment-text">{comment.body}</p>
        )}
      </div>
    </div>
  );
}
