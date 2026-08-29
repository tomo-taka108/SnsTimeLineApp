import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { ALLOWED_IMAGE_TYPES, MAX_IMAGE_SIZE_BYTES, resolveFileUrl, uploadFile } from "../api/files";
import type { PostImageSummary } from "../api/types";
import { useAuth } from "../auth/useAuth";
import { countChars, validatePostBody } from "../pages/validation";
import { Avatar } from "./Avatar";
import { ConfirmModal } from "./ConfirmModal";
import { Modal } from "./Modal";

const BODY_MAX = 280;
const WARN_THRESHOLD = BODY_MAX - 20;

type ComposerImage = { fileId: number; url: string };

type Props = {
  isOpen: boolean;
  /** MD-02 編集時は既存本文。MD-01 作成時は undefined */
  initialBody?: string;
  submitLabel: string;
  submittingLabel: string;
  onSubmit: (body: string, imageFileIds?: number[]) => Promise<void>;
  onClose: () => void;
  /** MD-02 は既存の画像を変更できない旨のヒントを出す */
  hint?: string;
  /** MD-01（作成）のみ true。画像の添付・削除を許可する */
  allowImage?: boolean;
  /** MD-02（編集）で既存の添付画像を読み取り専用表示するために渡す */
  existingImages?: PostImageSummary[];
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
  allowImage,
  existingImages,
}: Props) {
  const { user } = useAuth();
  const [body, setBody] = useState(initialBody ?? "");
  const [error, setError] = useState<string | undefined>(undefined);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showDiscardConfirm, setShowDiscardConfirm] = useState(false);
  const [image, setImage] = useState<ComposerImage | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const length = countChars(body);
  const isOver = length > BODY_MAX;
  const isEmpty = body.trim().length === 0;
  const counterClass = isOver ? "char-counter is-over" : length > WARN_THRESHOLD ? "char-counter is-warn" : "char-counter";
  const existingImage = existingImages?.[0];

  function resetAndClose() {
    setBody(initialBody ?? "");
    setError(undefined);
    // 作成用モーダルは key を持たず isOpen だけが切り替わるため、ここで画像も
    // 明示的にリセットしないと、投稿後に再度開いたとき前の画像が残ってしまう
    setImage(null);
    onClose();
  }

  function handleCloseRequest() {
    // 入力ありで閉じようとしたら確認する（docs/03_screen_design.md MD-01）。
    // 編集時は「元の本文と違う」場合のみ確認対象にする
    const hasChanges = body !== (initialBody ?? "") || image !== null;
    if (hasChanges && (body.trim().length > 0 || image !== null)) {
      setShowDiscardConfirm(true);
      return;
    }
    resetAndClose();
  }

  async function handleFileSelect(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    // クライアント側の検証。違反時はその場でエラーを出し、アップロードしない
    // （docs/03_screen_design.md MD-01）。実際の検証はサーバー側が行う
    if (!ALLOWED_IMAGE_TYPES.includes(file.type as (typeof ALLOWED_IMAGE_TYPES)[number])) {
      setError("対応していないファイル形式です（JPEG / PNG / WebP のみ）");
      return;
    }
    if (file.size > MAX_IMAGE_SIZE_BYTES) {
      setError("ファイルサイズが大きすぎます（5MBまで）");
      return;
    }

    setError(undefined);
    setIsUploading(true);
    try {
      const uploaded = await uploadFile(file);
      setImage({ fileId: uploaded.fileId, url: resolveFileUrl(uploaded.url) ?? uploaded.url });
    } catch {
      setError("画像のアップロードに失敗しました");
    } finally {
      setIsUploading(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const validationError = validatePostBody(body);
    setError(validationError);
    if (validationError) return;

    setIsSubmitting(true);
    try {
      await onSubmit(body.trim(), image ? [image.fileId] : undefined);
      // 成功時は呼び出し側がモーダルを閉じる（onClose は失敗時のみ手元で使う）
      setBody(initialBody ?? "");
      setImage(null);
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
            <button
              className="btn btn-accent btn-sm"
              type="submit"
              disabled={isEmpty || isOver || isSubmitting || isUploading}
            >
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
            {allowImage && image && (
              <div className="image-preview">
                <img src={image.url} alt="添付画像のプレビュー" />
                <button
                  className="remove-image"
                  type="button"
                  onClick={() => setImage(null)}
                  disabled={isSubmitting}
                  aria-label="画像を削除"
                >
                  ×
                </button>
              </div>
            )}
            {allowImage && isUploading && (
              <div className="image-preview" aria-live="polite">
                アップロード中...
              </div>
            )}
            {!allowImage && existingImage && (
              <div className="image-preview">
                <img src={resolveFileUrl(existingImage.url) ?? existingImage.url} alt="添付画像" />
              </div>
            )}
            {error && (
              <div className="field-error" role="alert">
                <span aria-hidden="true">⚠️</span>
                <span>{error}</span>
              </div>
            )}
            {hint && <div className="field-hint">{hint}</div>}
          </div>
          <div className="modal-foot">
            {allowImage ? (
              <>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept={ALLOWED_IMAGE_TYPES.join(",")}
                  onChange={handleFileSelect}
                  hidden
                />
                <button
                  className="btn btn-outline btn-sm"
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={isSubmitting || isUploading || image !== null}
                >
                  🖼 画像
                </button>
              </>
            ) : (
              <span />
            )}
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
