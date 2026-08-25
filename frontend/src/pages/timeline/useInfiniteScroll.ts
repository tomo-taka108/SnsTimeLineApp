import { useEffect, useRef } from "react";

/**
 * 無限スクロールの発火点（docs/03_screen_design.md SC-03）。
 *
 * IntersectionObserver ヘルパーは依存に追加せず、素の IntersectionObserver を使う
 * （frontend/README.md の依存最小方針）。下端から200px手前で発火する。
 *
 * callbackRef パターンで、onReachEnd が毎レンダで変わっても observer を作り直さない。
 * observer の作り直しは disconnect + observe の連続発火を招くため避ける。
 */
export function useInfiniteScroll(onReachEnd: () => void, enabled: boolean) {
  const sentinelRef = useRef<HTMLDivElement>(null);
  const callbackRef = useRef(onReachEnd);

  // レンダー中ではなく effect 内で更新する（react/refs ルール対策）。
  // このeffectは依存無しで毎回走るので、callbackRef は常に最新を指す
  useEffect(() => {
    callbackRef.current = onReachEnd;
  });

  useEffect(() => {
    const el = sentinelRef.current;
    if (!el || !enabled) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) callbackRef.current();
      },
      { rootMargin: "200px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [enabled]);

  return sentinelRef;
}
