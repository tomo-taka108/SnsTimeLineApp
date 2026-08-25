import { useEffect, useState } from "react";
import { fetchNewCount } from "../../api/posts";
import type { TimelineTab } from "../../api/types";

const POLL_INTERVAL_MS = 60_000;

/**
 * 新着投稿の件数をポーリングする（docs/09_decision_log.md D-31）。
 *
 * 設計書・モックアップのいずれにも無い今回追加の要望。60秒間隔、
 * document.hidden の間は停止し、復帰時に即1回叩いてから再開する。
 *
 * newestPostId は現在表示している先頭投稿の id。undefined（リスト空）のうちは動かない。
 */
export function useNewPostCount(tab: TimelineTab, newestPostId: number | undefined) {
  const [count, setCount] = useState(0);

  useEffect(() => {
    if (newestPostId === undefined) return;
    let cancelled = false;
    let timer: number | undefined;

    async function poll() {
      try {
        const res = await fetchNewCount(tab, newestPostId as number);
        if (!cancelled) setCount(res.count);
      } catch {
        // ポーリングの失敗は握りつぶす。裏の処理でトーストを出すと邪魔なだけ
      }
    }

    function start() {
      void poll();
      timer = window.setInterval(poll, POLL_INTERVAL_MS);
    }
    function stop() {
      if (timer !== undefined) {
        window.clearInterval(timer);
        timer = undefined;
      }
    }
    function handleVisibility() {
      if (document.hidden) {
        stop();
      } else if (timer === undefined) {
        start();
      }
    }

    if (!document.hidden) start();
    document.addEventListener("visibilitychange", handleVisibility);
    return () => {
      cancelled = true;
      stop();
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [tab, newestPostId]);

  return { count, reset: () => setCount(0) };
}
