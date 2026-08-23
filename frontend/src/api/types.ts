/**
 * バックエンドAPIの型定義（docs/05_api_design.md）。
 *
 * バックエンドのレスポンスと1対1で対応させる。勝手に項目を足さない。
 */

/** ユーザーの要約表現。email は含まれない（アカウント列挙の防止） */
export type UserSummary = {
  id: number;
  username: string;
  displayName: string;
  /** 未設定なら null。ファイルモジュールが未実装のため現状は常に null */
  avatarUrl: string | null;
};

/** #1 signup / #2 login のレスポンス */
export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  /** アクセストークンの有効秒数（900 = 15分） */
  expiresIn: number;
  user: UserSummary;
};

/** #27 refresh のレスポンス。user を含まない */
export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

/** バリデーションエラーの1フィールド分 */
export type FieldErrorItem = {
  /** バックエンドのDTOのフィールド名。フォームの name と一致する */
  field: string;
  message: string;
};

/** 統一エラーレスポンス（docs/05_api_design.md 1.3） */
export type ErrorResponse = {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  /** バリデーションエラー（400）のときだけ存在する */
  errors?: FieldErrorItem[];
};

/** エラーコード（docs/05_api_design.md 1.3 ＋ D-29 の INVALID_REFRESH_TOKEN） */
export const ErrorCode = {
  VALIDATION_ERROR: "VALIDATION_ERROR",
  INVALID_CREDENTIALS: "INVALID_CREDENTIALS",
  UNAUTHENTICATED: "UNAUTHENTICATED",
  INVALID_REFRESH_TOKEN: "INVALID_REFRESH_TOKEN",
  FORBIDDEN: "FORBIDDEN",
  NOT_FOUND: "NOT_FOUND",
  EMAIL_ALREADY_EXISTS: "EMAIL_ALREADY_EXISTS",
  USERNAME_ALREADY_EXISTS: "USERNAME_ALREADY_EXISTS",
  INTERNAL_ERROR: "INTERNAL_ERROR",
  /** 通信そのものに失敗した場合にフロント側で付ける（バックエンド由来ではない） */
  NETWORK_ERROR: "NETWORK_ERROR",
} as const;

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode];

/** signup のリクエスト。passwordConfirm は含めない（クライアント専用の項目のため） */
export type SignupPayload = {
  email: string;
  username: string;
  displayName: string;
  password: string;
};

export type LoginPayload = {
  email: string;
  password: string;
};
