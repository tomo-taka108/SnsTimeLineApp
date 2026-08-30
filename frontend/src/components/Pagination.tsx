type Props = {
  /** 0始まり */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
};

/** 現在ページの前後に何ページ分の番号を出すか */
const SIBLINGS = 1;

/**
 * 表示するページ番号の並びを作る。番号が飛ぶ位置には "…" を入れる。
 * 例（現在5ページ目・全20ページ）: 1 … 4 5 6 … 20
 */
function buildPages(page: number, totalPages: number): (number | "gap")[] {
  const pages: (number | "gap")[] = [];
  let previous = -1;

  for (let i = 0; i < totalPages; i++) {
    const isEdge = i === 0 || i === totalPages - 1;
    const isNearCurrent = Math.abs(i - page) <= SIBLINGS;
    if (!isEdge && !isNearCurrent) continue;

    // 直前に出した番号と離れていれば区切りを挟む
    if (previous >= 0 && i - previous > 1) {
      pages.push("gap");
    }
    pages.push(i);
    previous = i;
  }
  return pages;
}

/**
 * ページ番号のページネーション（mockup/search.html .pagination）。
 *
 * <b>SC-07（ユーザー検索）でのみ使う。</b> 他の一覧は無限スクロール（カーソル方式）であり、
 * ページ番号で行き来できるのは検索だけ（docs/05_api_design.md 2.2）。
 *
 * モックでは <a> だが、URLへの遷移ではなく状態更新なので button にする
 * （AppHeader のログアウトと同じ判断）。
 */
export function Pagination({ page, totalPages, onChange }: Props) {
  // 1ページに収まっているならページャ自体を出さない
  if (totalPages <= 1) return null;

  const isFirst = page === 0;
  const isLast = page === totalPages - 1;

  return (
    <nav className="pagination" aria-label="ページ送り">
      <button type="button" onClick={() => onChange(page - 1)} disabled={isFirst}>
        前へ
      </button>

      {buildPages(page, totalPages).map((item, index) =>
        item === "gap" ? (
          // 区切りは押せないので span。key は位置で決まるため index で足りる
          // eslint-disable-next-line react/no-array-index-key
          <span key={`gap-${index}`} className="gap" aria-hidden="true">
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            className={item === page ? "current" : undefined}
            aria-current={item === page ? "page" : undefined}
            onClick={() => onChange(item)}
          >
            {item + 1}
          </button>
        ),
      )}

      <button type="button" onClick={() => onChange(page + 1)} disabled={isLast}>
        次へ
      </button>
    </nav>
  );
}
