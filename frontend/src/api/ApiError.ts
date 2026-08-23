import { ErrorCode, type FieldErrorItem } from "./types";

/**
 * APIが返したエラー。
 *
 * 画面側は `code` を見て表示方法を決める（docs/03_screen_design.md 8章）。
 * - 400 VALIDATION_ERROR → フィールド直下
 * - 401 INVALID_CREDENTIALS → フォーム上部
 * - 409 *_ALREADY_EXISTS → 該当フィールド直下
 * - その他 → トースト
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldErrorItem[];

  constructor(status: number, code: string, message: string, fieldErrors: FieldErrorItem[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  /** 通信そのものに失敗した場合（サーバー停止・オフラインなど） */
  static network(): ApiError {
    return new ApiError(0, ErrorCode.NETWORK_ERROR, "通信に失敗しました。時間をおいて再度お試しください");
  }
}

/**
 * 409（重複）を、フォームのフィールドエラーに変換するための対応表。
 *
 * <b>409 のレスポンスには errors[] が無く、code だけが返る。</b>
 * そのため errors[] を見るだけでは「どの入力欄が悪いのか」が分からない。
 * バックエンドの実装を確認したうえでこの対応表を用意している。
 */
const CONFLICT_FIELD: Record<string, string> = {
  [ErrorCode.EMAIL_ALREADY_EXISTS]: "email",
  [ErrorCode.USERNAME_ALREADY_EXISTS]: "username",
};

/**
 * エラーを「フィールド名 → メッセージ」の形に正規化する。
 *
 * 400（errors[] あり）と 409（code のみ）の両方を同じ形にして、
 * フォーム側が区別せずに扱えるようにする。
 *
 * @returns フィールドに紐付けられなかった場合は空オブジェクト
 */
export function toFieldErrors(error: ApiError): Record<string, string> {
  // 400: errors[] をそのまま使う
  if (error.fieldErrors.length > 0) {
    const result: Record<string, string> = {};
    for (const item of error.fieldErrors) {
      // 同じフィールドに複数来た場合は最初のものを優先する
      if (!(item.field in result)) {
        result[item.field] = item.message;
      }
    }
    return result;
  }

  // 409: code から対応するフィールドを決める
  const field = CONFLICT_FIELD[error.code];
  if (field) {
    return { [field]: error.message };
  }

  return {};
}
