import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as usersApi from "../api/users";
import { useFollow } from "./useFollow";

/**
 * {@link useFollow} の単体テスト（docs/11_test_design.md 24.1章、ケース #420〜#424）。
 *
 * useLike とほぼ同形だが、followerCount が省略可能な点が異なる。
 * UserProfile（SC-05）は持つが、UserListItem（SC-08/09）は持たないため。
 */
const showToast = vi.fn();
vi.mock("../components/useToast", () => ({
  useToast: () => ({ showToast }),
}));

describe("useFollow", () => {
  beforeEach(() => {
    showToast.mockClear();
  });

  it("#420 未フォローなら、レスポンス前に即座にフォロー中になり +1 される", () => {
    vi.spyOn(usersApi, "followUser").mockReturnValue(new Promise(() => {}));
    const onChange = vi.fn();
    const user = { id: 1, isFollowing: false, followerCount: 5 };
    const { result } = renderHook(() => useFollow(user, onChange));

    act(() => {
      void result.current.toggle();
    });

    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ isFollowing: true, followerCount: 6 }),
    );
  });

  it("#421 レスポンス到着後はサーバーの値で上書きし、トーストを出す", async () => {
    vi.spyOn(usersApi, "unfollowUser").mockResolvedValue({ isFollowing: false, followerCount: 4 });
    const onChange = vi.fn();
    const user = { id: 1, isFollowing: true, followerCount: 5 };
    const { result } = renderHook(() => useFollow(user, onChange));

    await act(async () => {
      await result.current.toggle();
    });

    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ isFollowing: false, followerCount: 4 }),
    );
    expect(showToast).toHaveBeenCalledWith("フォローを解除しました");
  });

  /**
   * #422 型の緩さを突いたケース。followerCount を持たない UserListItem を渡したとき、
   * undefined + 1 = NaN になると一覧画面のフォローボタンだけが壊れる。
   */
  it("#422 followerCount が undefined なら undefined のまま（NaNにしない）", async () => {
    vi.spyOn(usersApi, "followUser").mockResolvedValue({ isFollowing: true, followerCount: 99 });
    const onChange = vi.fn();
    const user = { id: 1, isFollowing: false }; // followerCount なし
    const { result } = renderHook(() => useFollow(user, onChange));

    await act(async () => {
      await result.current.toggle();
    });

    // 楽観的更新・確定値ともに undefined を維持する
    for (const call of onChange.mock.calls) {
      expect(call[0].followerCount).toBeUndefined();
    }
  });

  it("#423 通信が失敗したら元に戻り、エラートーストを出す", async () => {
    vi.spyOn(usersApi, "followUser").mockRejectedValue(new Error("network"));
    const onChange = vi.fn();
    const user = { id: 1, isFollowing: false, followerCount: 5 };
    const { result } = renderHook(() => useFollow(user, onChange));

    await act(async () => {
      await result.current.toggle();
    });

    expect(onChange).toHaveBeenLastCalledWith(user);
    expect(showToast).toHaveBeenCalledWith(
      "通信に失敗しました。時間をおいて再度お試しください",
      true,
    );
  });

  it("#424 送信中に再度 toggle しても2回目は無視される", async () => {
    const followUser = vi.spyOn(usersApi, "followUser").mockReturnValue(new Promise(() => {}));
    const onChange = vi.fn();
    const user = { id: 1, isFollowing: false, followerCount: 5 };
    const { result } = renderHook(() => useFollow(user, onChange));

    act(() => {
      void result.current.toggle();
    });
    await waitFor(() => expect(result.current.isSubmitting).toBe(true));

    act(() => {
      void result.current.toggle();
    });

    expect(followUser).toHaveBeenCalledTimes(1);
  });
});
