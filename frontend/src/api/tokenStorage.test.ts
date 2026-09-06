import { beforeEach, describe, expect, it } from "vitest";
import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from "./tokenStorage";

/**
 * {@link tokenStorage.ts} の単体テスト（docs/11_test_design.md 23.4章、ケース #390〜#396）。
 *
 * jsdom の localStorage はテスト間で共有される。beforeEach で clear しないと
 * 前のテストの値が残る（D-56 の @Transactional ロールバックと同じ問題が別の場所で出る）。
 */
describe("tokenStorage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("#390 未保存の状態では getAccessToken が null を返す", () => {
    expect(getAccessToken()).toBeNull();
  });

  it("#391 未保存の状態では getRefreshToken が null を返す", () => {
    expect(getRefreshToken()).toBeNull();
  });

  it("#392 saveTokens 後は両方取得できる", () => {
    saveTokens("access-1", "refresh-1");
    expect(getAccessToken()).toBe("access-1");
    expect(getRefreshToken()).toBe("refresh-1");
  });

  /** #393 リフレッシュトークンは使い捨て（ローテーション）のため、必ず新しい値で上書きされる（D-29） */
  it("#393 saveTokens を2回呼ぶと必ず新しい値で上書きされる", () => {
    saveTokens("access-1", "refresh-1");
    saveTokens("access-2", "refresh-2");
    expect(getAccessToken()).toBe("access-2");
    expect(getRefreshToken()).toBe("refresh-2");
  });

  /**
   * #394 clearTokens が守っているのは D-07 の前提そのもの。片方だけ消すと
   * 「ログアウトしたのにトークンが残っている」状態になる。
   */
  it("#394 clearTokens はアクセストークンとリフレッシュトークンの両方を消す", () => {
    saveTokens("access-1", "refresh-1");
    clearTokens();
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it("#395 clear後に取得すると両方 null", () => {
    saveTokens("access-1", "refresh-1");
    clearTokens();
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it("#396 保存→取得→消去→取得の一連の流れで状態が正しく遷移する", () => {
    expect(getAccessToken()).toBeNull();
    saveTokens("a1", "r1");
    expect(getAccessToken()).toBe("a1");
    clearTokens();
    expect(getAccessToken()).toBeNull();
  });
});
