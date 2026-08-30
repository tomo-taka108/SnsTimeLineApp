import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/useAuth";
import { countChars, validateCommentBody } from "../pages/validation";
import { Avatar } from "./Avatar";

const BODY_MAX = 280;
const WARN_THRESHOLD = BODY_MAX - 20;

type Props = {
  onSubmit: (body: string) => Promise<void>;
  /** F-CM-03 コメント編集時は既存本文。新規投稿時は undefined（docs/09_decision_log.md D-51） */
  initialBody?: string;
  submitLabel?: string;
  /** 指定時のみ [キャンセル] ボタンを出す。編集モードの目印にもなる */
  onCancel?: () => void;
  autoFocus?: boolean;
};

/**
 * コメント投稿欄（SC-04、mockup/post-detail.html の .comment-form）。
 *
 * PostComposer はモーダル前提（破棄確認など）のため流用せず、常時表示用に軽量に作る。
 * 文字数カウンタのロジックは PostComposer と同じ考え方を踏襲する。
 *
 * F-CM-03 のインライン編集にも流用する（initialBody / onCancel を渡す）。編集時は
 * 成功してもクリアしない。閉じる判断は呼び出し側（CommentItem）が行う。
 */
export function CommentForm({ onSubmit, initialBody, submitLabel = "送信", onCancel, autoFocus }: Props) {
  const { user } = useAuth();
  const [body, setBody] = useState(initialBody ?? "");
  const [error, setError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const length = countChars(body);
  const isOver = length > BODY_MAX;
  const isEmpty = body.trim().length === 0;
  const counterClass = isOver ? "char-counter is-over" : length > WARN_THRESHOLD ? "char-counter is-warn" : "char-counter";

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const validationError = validateCommentBody(body);
    setError(validationError);
    if (validationError) return;

    setIsSubmitting(true);
    try {
      await onSubmit(body.trim());
      if (!onCancel) {
        setBody(""); // 新規投稿時のみクリア。編集時は呼び出し側が編集モードを閉じる
      }
    } catch {
      // エラートーストは呼び出し側（PostDetailPage）が出す。失敗時は入力を保持する
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!user) return null;

  return (
    <form className="comment-form" onSubmit={handleSubmit}>
      <Avatar user={user} size="sm" />
      <div className="grow">
        <textarea
          name="body"
          className="textarea"
          rows={2}
          value={body}
          onChange={(event) => setBody(event.target.value)}
          placeholder="コメントを入力..."
          disabled={isSubmitting}
          autoFocus={autoFocus}
        />
        {error && (
          <div className="field-error" role="alert">
            <span aria-hidden="true">⚠️</span>
            <span>{error}</span>
          </div>
        )}
        <div className="comment-form-foot">
          <span className={counterClass}>
            {length}/{BODY_MAX}
          </span>
          {onCancel && (
            <button className="btn btn-outline btn-sm" type="button" onClick={onCancel} disabled={isSubmitting}>
              キャンセル
            </button>
          )}
          <button className="btn btn-accent btn-sm" type="submit" disabled={isEmpty || isOver || isSubmitting}>
            {submitLabel}
          </button>
        </div>
      </div>
    </form>
  );
}
