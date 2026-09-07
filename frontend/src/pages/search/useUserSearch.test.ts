import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as usersApi from "../../api/users";
import type { OffsetPage, UserListItem } from "../../api/types";
import { useUserSearch } from "./useUserSearch";

/**
 * {@link useUserSearch} の単体テスト（docs/11_test_design.md 24.2章、ケース #425〜#430）。
 *
 * <b>fake timers と非同期処理を同時に扱う節。注意点が2つある。</b>
 *
 * 1. <b>vi.advanceTimersByTimeAsync()（非同期版）を使い、必ず await する。</b>
 *    同期版の advanceTimersByTime() はタイマーを進めるだけで、その後に続く
 *    Promise（fetchの解決）が処理されないため、状態が更新されない。
 *
 * 2. <b>waitFor を使わない。</b> waitFor は内部でタイマーを使ってポーリングするため、
 *    fake timers で時計を止めていると永久に待ち続けてタイムアウトする（実際に踏んだ）。
 *    advanceTimersByTimeAsync で時間を進めた時点で状態は確定しているので、
 *    そのまま同期的に assert すればよい。
 *
 * 3. <b>時間を進める操作は act() で包む。</b> setState が Promise のコールバック内で
 *    呼ばれるため、act の外だと React が再レンダーを確定させず、状態が古いまま見える
 *    （これも実際に踏んだ。1と2を直しただけでは通らなかった）。
 */
function page(items: UserListItem[]): OffsetPage<UserListItem> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: 1 };
}

function user(id: number, username: string): UserListItem {
  return {
    id,
    username,
    displayName: username,
    avatarUrl: null,
    bio: null,
    isFollowing: false,
    isMe: false,
  };
}

describe("useUserSearch", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** #425 未入力ではAPIを呼ばない。q が空だとサーバーは400を返すため */
  it("#425 未入力ではAPIを呼ばず status が initial になる", () => {
    const searchUsers = vi.spyOn(usersApi, "searchUsers");
    const { result } = renderHook(() => useUserSearch("", 0));

    expect(result.current.status).toBe("initial");
    expect(searchUsers).not.toHaveBeenCalled();
  });

  it("#426 入力から299msの時点ではまだ呼ばない", async () => {
    const searchUsers = vi.spyOn(usersApi, "searchUsers").mockResolvedValue(page([]));
    renderHook(() => useUserSearch("taro", 0));

    await vi.advanceTimersByTimeAsync(299);

    expect(searchUsers).not.toHaveBeenCalled();
  });

  it("#427 入力から300ms経過すると1回だけ呼ぶ", async () => {
    const searchUsers = vi.spyOn(usersApi, "searchUsers").mockResolvedValue(page([user(1, "taro")]));
    renderHook(() => useUserSearch("taro", 0));

    await vi.advanceTimersByTimeAsync(300);

    expect(searchUsers).toHaveBeenCalledTimes(1);
    expect(searchUsers).toHaveBeenCalledWith("taro", 0, expect.anything());
  });

  /** #428 連続入力では前のタイマーがキャンセルされ、最後の1回だけが実行される */
  it("#428 連続入力しても最後の1回だけ呼ばれる", async () => {
    const searchUsers = vi.spyOn(usersApi, "searchUsers").mockResolvedValue(page([]));
    const { rerender } = renderHook(({ q }) => useUserSearch(q, 0), {
      initialProps: { q: "ta" },
    });

    await vi.advanceTimersByTimeAsync(100);
    rerender({ q: "tar" });
    await vi.advanceTimersByTimeAsync(100);
    rerender({ q: "taro" });
    await vi.advanceTimersByTimeAsync(300);

    expect(searchUsers).toHaveBeenCalledTimes(1);
    expect(searchUsers).toHaveBeenCalledWith("taro", 0, expect.anything());
  });

  /**
   * #429 古い結果が後から到着しても採用しない（requestId ガード）。
   * AbortController だけでは、中断が間に合わずレスポンスが確定する場合がある。
   */
  it("#429 古いリクエストの結果が後から届いても表示を上書きしない", async () => {
    let resolveOld: ((value: OffsetPage<UserListItem>) => void) | undefined;
    vi.spyOn(usersApi, "searchUsers")
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce(page([user(2, "taro")]));

    const { result, rerender } = renderHook(({ q }) => useUserSearch(q, 0), {
      initialProps: { q: "ta" },
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(300); // 「ta」の検索が始まる（未解決のまま）
    });
    rerender({ q: "taro" });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300); // 「taro」の検索が完了する
    });

    expect(result.current.searchedQuery).toBe("taro");

    // ここで「ta」の結果が遅れて到着する
    await act(async () => {
      resolveOld?.(page([user(1, "ta")]));
      await vi.advanceTimersByTimeAsync(0);
    });

    // 「taro」の結果が維持されている
    expect(result.current.searchedQuery).toBe("taro");
    expect(result.current.users).toEqual([user(2, "taro")]);
  });

  it("#430 通信が失敗すると status が error になる", async () => {
    vi.spyOn(usersApi, "searchUsers").mockRejectedValue(new Error("network"));
    const { result } = renderHook(() => useUserSearch("taro", 0));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(300);
    });

    expect(result.current.status).toBe("error");
  });
});
