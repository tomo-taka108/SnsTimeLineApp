import { useState } from "react";
import { resolveFileUrl } from "../api/files";
import type { PostImageSummary } from "../api/types";
import { ImageLightbox } from "./ImageLightbox";

type Props = {
  image: PostImageSummary;
};

/**
 * 投稿カード・投稿詳細に表示する添付画像（docs/03_screen_design.md 5.1）。
 *
 * `<button>` にしてキーボードでも拡大表示を開けるようにする。クリック時は
 * `stopPropagation` して、カード全体クリックでの投稿詳細への遷移を止める。
 */
export function PostImage({ image }: Props) {
  const [isLightboxOpen, setIsLightboxOpen] = useState(false);
  const url = resolveFileUrl(image.url) ?? image.url;

  return (
    <>
      <button
        className="post-image"
        type="button"
        onClick={(event) => {
          event.stopPropagation();
          setIsLightboxOpen(true);
        }}
        aria-label="画像を拡大表示"
      >
        <img src={url} alt="" width={image.width ?? undefined} height={image.height ?? undefined} loading="lazy" />
      </button>
      <ImageLightbox isOpen={isLightboxOpen} url={url} onClose={() => setIsLightboxOpen(false)} />
    </>
  );
}
