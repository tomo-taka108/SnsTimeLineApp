import { useEffect } from "react";

type Props = {
  isOpen: boolean;
  url: string;
  onClose: () => void;
};

/**
 * 投稿画像の拡大表示（docs/03_screen_design.md 5.1「クリックで拡大表示」）。
 *
 * {@link Modal} を再利用せず専用に作る（docs/09_decision_log.md D-46）。`.modal` は
 * 白背景・パディング前提のレイアウトで、画像を大きく見せる用途に合わないため。
 * Escape・背景クリックで閉じる、開いている間は背面のスクロールを止める、という
 * 挙動は Modal と同じパターンで書く。
 */
export function ImageLightbox({ isOpen, url, onClose }: Props) {
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
      className="lightbox-backdrop"
      role="dialog"
      aria-modal="true"
      aria-label="画像の拡大表示"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <button className="lightbox-close" type="button" onClick={onClose} aria-label="閉じる">
        ×
      </button>
      <img className="lightbox-img" src={url} alt="" />
    </div>
  );
}
