/** 投稿カードのスケルトン（mockup/states.html）。ローディング中はスピナーではなくこちらを出す */
export function PostCardSkeleton() {
  return (
    <div className="skeleton-card">
      <div className="sk sk-avatar" />
      <div style={{ flex: 1 }}>
        <div className="sk sk-line sk-w-30" />
        <div className="sk sk-line sk-w-100" />
        <div className="sk sk-line sk-w-60" />
      </div>
    </div>
  );
}
