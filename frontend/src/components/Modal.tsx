import { useEffect, type ReactNode } from "react";

type Props = {
  isOpen: boolean;
  onClose: () => void;
  size?: "sm";
  children: ReactNode;
};

/**
 * モーダルの共通土台（mockup/common.css .modal-backdrop / .modal）。
 *
 * 背景クリックと Escape で閉じる。開いている間は背面のスクロールを止める。
 */
export function Modal({ isOpen, onClose, size, children }: Props) {
  useEffect(() => {
    if (!isOpen) return;

    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleEscape);

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handleEscape);
      document.body.style.overflow = previousOverflow;
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  return (
    <div
      className="modal-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className={size === "sm" ? "modal modal-sm" : "modal"}>{children}</div>
    </div>
  );
}
