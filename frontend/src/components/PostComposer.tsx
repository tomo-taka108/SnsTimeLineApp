import { useState, type FormEvent } from "react";
import { useAuth } from "../auth/useAuth";
import { countChars, validatePostBody } from "../pages/validation";
import { Avatar } from "./Avatar";
import { ConfirmModal } from "./ConfirmModal";
import { Modal } from "./Modal";

const BODY_MAX = 280;
const WARN_THRESHOLD = BODY_MAX - 20;

type Props = {
  isOpen: boolean;
  /** MD-02 編集時は既存本文。MD-01 作成時は undefined */
  initialBody?: string;
  submitLabel: string;
  submittingLabel: string;
  onSubmit: (body: string) => Promise<void>;
  onClose: () => void;
  /** MD-02 は既存の画像を変更できない旨のヒントを出す（今回は画像自体が未実装） */
  hint?: string;
};

/**
 * MD-01 投稿作成モーダル / MD-02 投稿編集モーダル 共通の中身。
 *
 * FormField は input 専用（textarea が無い）ため、ここで新規にマークアップする
 * （mockup/common.css の .compose / .compose textarea）。
 */
export function PostComposer({
  isOpen,
  initialBody,
  submitLabel,
  submittingLabel,
  onSubmit,
  onClose,
  hint,
}: Props) {
  const { user } = useAuth();
  const [body, setBody] = useState(initialBody ?? "");
  const [error, setError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);

  const length = countChars(body);
  const isOver = length > BODY_MAX;
  const isEmpty = body.trim().length === 0;
  const counterClass = isOver ? "char-counter is-over" : length > WARN_THRESHOLD ? "char-counter is-warn" : "char-counter";

  function resetAndClose() {
    setBody(initialBody ?? "");
    setError(undefined);
    onClose();
  }

  function handleCloseRequest() {
    // 入力ありで閉じようとしたら確認する（docs/03_screen_design.md MD-01）。
    // 編集時は「元の本文と違う」場合のみ確認対象にする
    const hasChanges = body !== (initialBody ?? "");
    if (hasChanges && body.trim().length > 0) {
      setShowDiscardConfirm(true);
      return;
    }
    resetAndClose();
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const validationError = validatePostBody(body);
    setError(validationError);
    if (validationError) return;

    setIsSubmitting(true);
    try {
      await onSubmit(body.trim());
      // 成功時は呼び出し側がモーダルを閉じる（onClose は失敗時のみ手元で使う）
      setBody(initialBody ?? "");
    } catch {
      // 失敗時はモーダルを閉じず、入力を保持する（docs/03_screen_design.md MD-01）。
      // 個別のエラーメッセージは呼び出し側で toast を出す
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!user) return null;

  return (
    <>
      <Modal isOpen={isOpen && !showDiscardConfirm} onClose={handleCloseRequest}>
        <form onSubmit={handleSubmit}>
          <div className="modal-head">
            <button className="modal-close" type="button" onClick={handleCloseRequest} aria-label="閉じる">
              ×
            </button>
            <button className="btn btn-accent btn-sm" type="submit" disabled={isEmpty || isOver || isSubmitting}>
              {isSubmitting ? submittingLabel : submitLabel}
            </button>
          </div>
          <div className="modal-body">
            <div className="compose">
              <Avatar user={user} />
              <textarea
                name="body"
                value={body}
                onChange={(event) => setBody(event.target.value)}
                placeholder="いまどうしてる？"
                disabled={isSubmitting}
                autoFocus
              />
            </div>
            {error && (
              <div className="field-error" role="alert">
                <span aria-hidden="true">⚠️</span>
                <span>{error}</span>
              </div>
            )}
            {hint && <div className="field-hint">{hint}</div>}
          </div>
          <div className="modal-foot">
            <span />
            <span className={counterClass}>
              {length}/{BODY_MAX}
            </span>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        isOpen={showDiscardConfirm}
        title="投稿を破棄しますか？"
        message="入力した内容は保存されません。"
        confirmLabel="破棄する"
        isDanger
        onConfirm={() => {
          setShowDiscardConfirm(false);
          resetAndClose();
        }}
        onCancel={() => setShowDiscardConfirm(false)}
      />
    </>
  );
}
