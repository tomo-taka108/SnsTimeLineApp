import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import * as postsApi from "../../api/posts";
import { useNewPostCount } from "./useNewPostCount";

/**
 * {@link useNewPostCount} の単体テスト（docs/11_test_design.md 24.2章、ケース #431〜#433）。
 *
 * 60秒間隔のポーリング。document.hidden の間は停止し、復帰時に即1回叩いてから再開する
 * （docs/09_decision_log.md D-31）。
 *
 * fake timers の扱いは useUserSearch.test.ts と同じ（advanceTimersByTimeAsync を act で包む）。
 */

/** document.hidden は読み取り専用のため defineProperty で差し替える */
function setHidden(hidden: boolean) {
  Object.defineProperty(document, "hidden", { value: hidden, configurable: true });
  document.dispatchEvent(new Event("visibilitychange"));
}

describe("useNewPostCount", () => {
  /**
   * 各テストで unmount する。しないと前のテストのフックが setInterval を持ったまま残り、
   * 次のテストで時間を進めたときに一緒に発火して呼び出し回数がずれる
   * （実際に踏んだ。#433 が「2のはずが4」になった）。
   */
  let cleanup: (() => void) | undefined;

  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(document, "hidden", { value: false, configurable: true });
  });

  afterEach(() => {
    cleanup?.();
    cleanup = undefined;
    vi.useRealTimers();
  });

  /** #431 リストが空（newestPostId が undefined）のうちはポーリングを開始しない */
  it("#431 newestPostId が undefined ならポーリングを開始しない", async () => {
    const fetchNewCount = vi.spyOn(postsApi, "fetchNewCount");
    cleanup = renderHook(() => useNewPostCount("all", undefined)).unmount;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });

    expect(fetchNewCount).not.toHaveBeenCalled();
  });

  it("#431b newestPostId があれば即座に1回叩き、60秒ごとに繰り返す", async () => {
    const fetchNewCount = vi.spyOn(postsApi, "fetchNewCount").mockResolvedValue({ count: 2 });
    const rendered = renderHook(() => useNewPostCount("all", 100));
    cleanup = rendered.unmount;
    const { result } = rendered;

    // マウント直後に1回
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(fetchNewCount).toHaveBeenCalledTimes(1);
    expect(result.current.count).toBe(2);

    // 60秒後にもう1回
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(fetchNewCount).toHaveBeenCalledTimes(2);
  });

  /** #432 裏タブで通信し続けないよう、hidden の間は止める */
  it("#432 document.hidden になるとポーリングを停止する", async () => {
    const fetchNewCount = vi.spyOn(postsApi, "fetchNewCount").mockResolvedValue({ count: 0 });
    cleanup = renderHook(() => useNewPostCount("all", 100)).unmount;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    const callsBeforeHidden = fetchNewCount.mock.calls.length;

    await act(async () => {
      setHidden(true);
      await vi.advanceTimersByTimeAsync(180_000); // 3分放置
    });

    // 停止しているので増えていない
    expect(fetchNewCount).toHaveBeenCalledTimes(callsBeforeHidden);
  });

  /** #433 復帰時は60秒待たずに即1回叩く。待つと復帰後しばらく新着に気づけない */
  it("#433 復帰すると即座に1回叩いてから再開する", async () => {
    const fetchNewCount = vi.spyOn(postsApi, "fetchNewCount").mockResolvedValue({ count: 5 });
    cleanup = renderHook(() => useNewPostCount("all", 100)).unmount;

    // マウント直後の1回を消化してから hidden にする
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    await act(async () => {
      setHidden(true);
      await vi.advanceTimersByTimeAsync(120_000);
    });
    const callsWhileHidden = fetchNewCount.mock.calls.length;

    await act(async () => {
      setHidden(false);
      await vi.advanceTimersByTimeAsync(0); // 時間を進めずとも1回叩く
    });

    expect(fetchNewCount.mock.calls.length).toBe(callsWhileHidden + 1);
  });

  it("#433b reset() でカウントが0に戻る", async () => {
    vi.spyOn(postsApi, "fetchNewCount").mockResolvedValue({ count: 7 });
    const rendered = renderHook(() => useNewPostCount("all", 100));
    cleanup = rendered.unmount;
    const { result } = rendered;

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.count).toBe(7);

    act(() => {
      result.current.reset();
    });
    expect(result.current.count).toBe(0);
  });
});
