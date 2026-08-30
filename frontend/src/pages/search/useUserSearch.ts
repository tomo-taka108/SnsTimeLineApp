import { useEffect, useRef, useState } from "react";
import { searchUsers } from "../../api/users";
import type { UserListItem } from "../../api/types";

/**
 * 検索欄に入力してからAPIを呼ぶまでの待ち時間（docs/03_screen_design.md SC-07）。
 * 1文字ごとに叩かず、入力が落ち着いてから1回だけ投げる。
 */
const DEBOUNCE_MS = 300;

/**
 * initial: 未入力。<b>APIを呼ばない</b>（q が空だとサーバーは400を返す）。
 * ready は0件も含む（0件表示は呼び出し側が users.length で判定する）。
 */
type Status = "initial" | "loading" | "ready" | "error";

/** 検索1回ぶんの結果。キーワードと結果を1つの状態にまとめて持つ */
type Result = {
  /** この結果がどのキーワードのものか。0件メッセージの文言に使う */
  query: string;
  users: UserListItem[];
  totalPages: number;
  totalElements: number;
  status: Exclude<Status, "initial">;
};

const LOADING: Result = { query: "", users: [], totalPages: 0, totalElements: 0, status: "loading" };

/**
 * SC-07（ユーザー検索）の状態管理（docs/03_screen_design.md SC-07 / docs/05_api_design.md #20）。
 *
 * useFollowList と同形だが、違いが3つある。
 * 1. カーソルではなく<b>ページ番号</b>を持つ（#20 だけがオフセット方式）
 * 2. 入力に対する<b>デバウンス</b>が入る
 * 3. <b>未入力ではAPIを呼ばない</b>（status="initial"）
 */
export function useUserSearch(q: string, page: number) {
  const keyword = q.trim();
  const [result, setResult] = useState<Result>(LOADING);
  const requestIdRef = useRef(0);

  useEffect(() => {
    // 未入力ではリクエストを投げない。
    // 進行中のリクエストの結果が後から着弾しても採用しないよう、IDだけ進めておく
    if (!keyword) {
      requestIdRef.current++;
      return;
    }

    const requestId = ++requestIdRef.current;
    const controller = new AbortController();

    const timer = setTimeout(() => {
      async function load() {
        // デバウンス待ちの間は前回の結果を維持し、実際に投げる直前で loading にする。
        // 入力のたびにスケルトンへ戻ると、ちらついて読めない（docs/03_screen_design.md SC-07）
        setResult((prev) => ({ ...prev, status: "loading" }));
        try {
          const page_ = await searchUsers(keyword, page, controller.signal);
          // 「ta」の結果が「taro」の後に届いて上書きするのを防ぐ二重の防御。
          // AbortController だけでは、中断が間に合わずレスポンスが確定する場合がある
          if (requestId !== requestIdRef.current) return;
          setResult({
            query: keyword,
            users: page_.items,
            totalPages: page_.totalPages,
            totalElements: page_.totalElements,
            status: "ready",
          });
        } catch (error) {
          // 中断は「失敗」ではない。新しい入力の結果が来るのでエラー表示にしない
          if (error instanceof DOMException && error.name === "AbortError") return;
          if (requestId !== requestIdRef.current) return;
          setResult({ ...LOADING, status: "error" });
        }
      }
      void load();
    }, DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [keyword, page]);

  /** フォロー状態が変わった行を差し替える（useFollowList と同じ） */
  function replaceUser(updated: UserListItem) {
    setResult((prev) => ({
      ...prev,
      users: prev.users.map((u) => (u.id === updated.id ? updated : u)),
    }));
  }

  // 未入力の表示はレンダー中に導出する。effect で setState して初期状態に戻すと
  // 余計な再レンダーが1回挟まるうえ、「前回の結果が一瞬見える」ことがある
  if (!keyword) {
    return {
      users: [],
      status: "initial" as const,
      totalPages: 0,
      totalElements: 0,
      searchedQuery: "",
      replaceUser,
    };
  }

  return {
    users: result.users,
    status: result.status as Status,
    totalPages: result.totalPages,
    totalElements: result.totalElements,
    searchedQuery: result.query,
    replaceUser,
  };
}
