import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import * as postsApi from "../api/posts";
import type { PostSummary } from "../api/types";
import { useLike } from "./useLike";

/**
 * {@link useLike} の単体テスト（docs/11_test_design.md 24.1章、ケース #413〜#419）。
 *
 * 楽観的UI更新: クリック直後に見た目を先に変え、レスポンスで実値に置き換える。
 * 失敗したら元に戻す（docs/03_screen_design.md SC-05）。
 *
 * useToast を使うため、ToastProvider の代わりに showToast をモックする。
 */
const showToast = vi.fn();
vi.mock("../components/useToast", () => ({
  useToast: () => ({ showToast }),
}));

function post(overrides: Partial<PostSummary> = {}): PostSummary {
  return {
    id: 1,
    body: "テスト投稿",
    createdAt: "2026-08-15T00:00:00Z",
    editedAt: null,
    likeCount: 3,
    commentCount: 0,
    isLikedByMe: false,
    author: { id: 10, username: "taro", displayName: "たろう", avatarUrl: null },
    images: [],
    ...overrides,
  };
}

describe("useLike", () => {
  beforeEach(() => {
    showToast.mockClear();
  });

  it("#413 未いいねなら、レスポンス前に即座に +1 されて反転する", async () => {
    // レスポンスは保留にして、楽観的更新だけを観測する
    vi.spyOn(postsApi, "likePost").mockReturnValue(new Promise(() => {}));
    const onChange = vi.fn();
    const { result } = renderHook(() => useLike(post(), onChange));

    act(() => {
      void result.current.toggle();
    });

    // 1回目の呼び出しが楽観的更新
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ isLikedByMe: true, likeCount: 4 }),
    );
  });

  it("#414 レスポンス到着後はサーバーの値で上書きされる", async () => {
    vi.spyOn(postsApi, "likePost").mockResolvedValue({ likeCount: 99, isLikedByMe: true });
    const onChange = vi.fn();
    const { result } = renderHook(() => useLike(post(), onChange));

    await act(async () => {
      await result.current.toggle();
    });

    // 最後の呼び出しがサーバーの確定値
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ likeCount: 99, isLikedByMe: true }),
    );
  });

  it("#415 いいね済みなら解除されて -1 される", async () => {
    vi.spyOn(postsApi, "unlikePost").mockResolvedValue({ likeCount: 2, isLikedByMe: false });
    const onChange = vi.fn();
    const { result } = renderHook(() => useLike(post({ isLikedByMe: true }), onChange));

    await act(async () => {
      await result.current.toggle();
    });

    expect(onChange).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({ isLikedByMe: false, likeCount: 2 }),
    );
  });

  /**
   * #416 本節の中心。通信が失敗したときだけ起きるため、手で確認するには
   * ネットワークを切るしかない。ロールバックを忘れると「いいねしたはずが消える」。
   */
  it("#416 通信が失敗したら元の値に完全に戻り、エラートーストを出す", async () => {
    vi.spyOn(postsApi, "likePost").mockRejectedValue(new Error("network"));
    const original = post();
    const onChange = vi.fn();
    const { result } = renderHook(() => useLike(original, onChange));

    await act(async () => {
      await result.current.toggle();
    });

    // 最後の呼び出しで元のオブジェクトそのものに戻っている
    expect(onChange).toHaveBeenLastCalledWith(original);
    expect(showToast).toHaveBeenCalledWith(
      "通信に失敗しました。時間をおいて再度お試しください",
      true,
    );
  });

  it("#417 いいね済みからの解除が失敗しても元に戻る", async () => {
    vi.spyOn(postsApi, "unlikePost").mockRejectedValue(new Error("network"));
    const original = post({ isLikedByMe: true });
    const onChange = vi.fn();
    const { result } = renderHook(() => useLike(original, onChange));

    await act(async () => {
      await result.current.toggle();
    });

    expect(onChange).toHaveBeenLastCalledWith(original);
  });

  it("#418 likeCount が 0 のとき解除しても負数にならない", async () => {
    vi.spyOn(postsApi, "unlikePost").mockReturnValue(new Promise(() => {}));
    const onChange = vi.fn();
    const { result } = renderHook(() =>
      useLike(post({ likeCount: 0, isLikedByMe: true }), onChange),
    );

    act(() => {
      void result.current.toggle();
    });

    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ likeCount: 0 }));
  });

  /** #419 冪等なAPIだが、多重リクエスト自体を避ける（連打防止） */
  it("#419 送信中に再度 toggle しても2回目は無視される", async () => {
    const likePost = vi.spyOn(postsApi, "likePost").mockReturnValue(new Promise(() => {}));
    const onChange = vi.fn();
    const { result } = renderHook(() => useLike(post(), onChange));

    act(() => {
      void result.current.toggle();
    });
    await waitFor(() => expect(result.current.isSubmitting).toBe(true));

    act(() => {
      void result.current.toggle();
    });

    expect(likePost).toHaveBeenCalledTimes(1);
  });
});
