import { describe, expect, it } from "vitest";
import { ApiError, toFieldErrors } from "./ApiError";
import { ErrorCode } from "./types";

/**
 * {@link ApiError.ts} の単体テスト（docs/11_test_design.md 23.3章、ケース #377〜#385）。
 *
 * toFieldErrors には3つの経路がある。
 * ① fieldErrors が1件以上ある（400 VALIDATION_ERROR）
 * ② fieldErrors は空で、code が CONFLICT_FIELD テーブルにある（409）
 * ③ どちらでもない
 */
describe("toFieldErrors", () => {
  it("#377 経路①: fieldErrors 1件をそのままフィールド名に変換する", () => {
    const error = new ApiError(400, ErrorCode.VALIDATION_ERROR, "検証エラー", [
      { field: "email", message: "メールアドレスの形式が正しくありません" },
    ]);
    expect(toFieldErrors(error)).toEqual({ email: "メールアドレスの形式が正しくありません" });
  });

  it("#378 経路①: fieldErrors 2件（別フィールド）は両方反映する", () => {
    const error = new ApiError(400, ErrorCode.VALIDATION_ERROR, "検証エラー", [
      { field: "email", message: "メールエラー" },
      { field: "username", message: "ユーザー名エラー" },
    ]);
    expect(toFieldErrors(error)).toEqual({ email: "メールエラー", username: "ユーザー名エラー" });
  });

  /** #379 同一フィールドに複数のエラーが来た場合、最初の1件だけを採用する（in ガード） */
  it("#379 経路①: 同一フィールドに2件あれば最初のメッセージのみ採用する", () => {
    const error = new ApiError(400, ErrorCode.VALIDATION_ERROR, "検証エラー", [
      { field: "email", message: "最初のエラー" },
      { field: "email", message: "2番目のエラー" },
    ]);
    expect(toFieldErrors(error)).toEqual({ email: "最初のエラー" });
  });

  it("#380 経路②: 409 EMAIL_ALREADY_EXISTS は email フィールドに変換する", () => {
    const error = new ApiError(409, ErrorCode.EMAIL_ALREADY_EXISTS, "既に登録されています");
    expect(toFieldErrors(error)).toEqual({ email: "既に登録されています" });
  });

  it("#381 経路②: 409 USERNAME_ALREADY_EXISTS は username フィールドに変換する", () => {
    const error = new ApiError(409, ErrorCode.USERNAME_ALREADY_EXISTS, "既に使われています");
    expect(toFieldErrors(error)).toEqual({ username: "既に使われています" });
  });

  /**
   * #382 表を作って初めて出てきた組み合わせ。実装は fieldErrors.length > 0 を先に見るため、
   * 409 でも errors[] があればそちらが勝つ（経路②に落ちない）。現状のバックエンドは409に
   * errors[] を付けないため実際には起きないが、将来両方返し始めたときの挙動を固定しておく。
   */
  it("#382 経路①が優先: fieldErrorsがあれば409コードのテーブルより優先される", () => {
    const error = new ApiError(409, ErrorCode.EMAIL_ALREADY_EXISTS, "既に登録されています", [
      { field: "username", message: "こちらが優先されるはず" },
    ]);
    expect(toFieldErrors(error)).toEqual({ username: "こちらが優先されるはず" });
  });

  it("#383 経路③: 401 INVALID_CREDENTIALS はフィールドに紐付けられない", () => {
    const error = new ApiError(401, ErrorCode.INVALID_CREDENTIALS, "認証に失敗しました");
    expect(toFieldErrors(error)).toEqual({});
  });

  it("#384 経路③: NETWORK_ERROR もフィールドに紐付けられない", () => {
    const error = new ApiError(0, ErrorCode.NETWORK_ERROR, "通信に失敗しました");
    expect(toFieldErrors(error)).toEqual({});
  });
});

describe("ApiError.network", () => {
  it("#385 status=0、既定のコードと文言を持つ", () => {
    const error = ApiError.network();
    expect(error.status).toBe(0);
    expect(error.code).toBe(ErrorCode.NETWORK_ERROR);
    expect(error.message).toBe("通信に失敗しました。時間をおいて再度お試しください");
  });
});
