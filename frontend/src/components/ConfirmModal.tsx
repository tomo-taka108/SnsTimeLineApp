import { Modal } from "./Modal";

type Props = {
  isOpen: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  /** 削除など破壊的な操作なら true（赤いボタンになる） */
  isDanger?: boolean;
  isSubmitting?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * 確認モーダル（MD-03 削除確認、および MD-01 の「投稿を破棄しますか？」で共用する）。
 */
export function ConfirmModal({
  isOpen,
  title,
  message,
  confirmLabel,
  isDanger = false,
  isSubmitting = false,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <Modal isOpen={isOpen} onClose={onCancel} size="sm">
      <div className="confirm-body">
        <h3>{title}</h3>
        <p>{message}</p>
      </div>
      <div className="confirm-foot">
        <button className="btn btn-outline" type="button" onClick={onCancel} disabled={isSubmitting}>
          キャンセル
        </button>
        <button
          className={isDanger ? "btn btn-danger" : "btn btn-accent"}
          type="button"
          onClick={onConfirm}
          disabled={isSubmitting}
        >
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}
