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

/** エラーコード（docs/05_api_design.md 1.3 ＋ D-29 の INVALID_REFRESH_TOKEN、D-39 の SELF_FOLLOW_NOT_ALLOWED） */
export const ErrorCode = {
  VALIDATION_ERROR: "VALIDATION_ERROR",
  SELF_FOLLOW_NOT_ALLOWED: "SELF_FOLLOW_NOT_ALLOWED",
  INVALID_CREDENTIALS: "INVALID_CREDENTIALS",
  UNAUTHENTICATED: "UNAUTHENTICATED",
  INVALID_REFRESH_TOKEN: "INVALID_REFRESH_TOKEN",
  FORBIDDEN: "FORBIDDEN",
  NOT_FOUND: "NOT_FOUND",
  EMAIL_ALREADY_EXISTS: "EMAIL_ALREADY_EXISTS",
  USERNAME_ALREADY_EXISTS: "USERNAME_ALREADY_EXISTS",
  /** 画像アップロード（#25）。5MB超 */
  FILE_TOO_LARGE: "FILE_TOO_LARGE",
  /** 画像アップロード（#25）。JPEG/PNG/WebP 以外、または実体が画像でない */
  UNSUPPORTED_MEDIA_TYPE: "UNSUPPORTED_MEDIA_TYPE",
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

/** 投稿に添付された画像。投稿への添付（F-PO-02）は次のPRで対応するため、現状は常に空配列 */
export type PostImageSummary = {
  fileId: number;
  url: string;
  width: number;
  height: number;
};

/** #25 画像アップロードのレスポンス。width / height は取得できなければ null */
export type UploadFileResponse = {
  fileId: number;
  url: string;
  width: number | null;
  height: number | null;
};

/** 投稿（タイムライン・詳細で共通、docs/05_api_design.md 4章 PostSummary）。 */
export type PostSummary = {
  id: number;
  author: UserSummary;
  body: string;
  images: PostImageSummary[];
  likeCount: number;
  commentCount: number;
  isLikedByMe: boolean;
  createdAt: string;
  /** null でなければ「編集済み」を表示する */
  editedAt: string | null;
};

/** カーソルページネーションの共通レスポンス（docs/05_api_design.md 4章 CursorPage） */
export type CursorPage<T> = {
  items: T[];
  /** hasNext が false のときは null */
  nextCursor: string | null;
  hasNext: boolean;
};

/** #29 GET /timeline/new-count のレスポンス。設計書#1〜#28には無い独自API（D-31） */
export type NewCountResponse = {
  count: number;
};

export type TimelineTab = "all" | "following";

/** #6 投稿作成のリクエスト。imageFileIds は画像機能未実装のため含めない */
export type CreatePostPayload = {
  body: string;
};

/** #8 投稿編集のリクエスト */
export type UpdatePostPayload = {
  body: string;
};

/**
 * コメント（docs/05_api_design.md 4章 Comment）。
 *
 * editedAt はコメント編集（#12）がPhase2のため今回は常に null。
 */
export type Comment = {
  id: number;
  author: UserSummary;
  body: string;
  isMine: boolean;
  createdAt: string;
  editedAt: string | null;
};

/** #11 コメント投稿のリクエスト */
export type CreateCommentPayload = {
  body: string;
};

/** #11 コメント投稿のレスポンス */
export type CreateCommentResponse = {
  comment: Comment;
  commentCount: number;
};

/** #13 コメント削除のレスポンス */
export type DeleteCommentResponse = {
  commentCount: number;
};

/** #14 / #15 いいね・いいね解除のレスポンス */
export type LikeResponse = {
  likeCount: number;
  isLikedByMe: boolean;
};

/**
 * #17 プロフィール取得のレスポンス（docs/05_api_design.md 4章 UserProfile）。
 *
 * postCount / followingCount / followerCount は非正規化カウンタを持たず、都度算出される
 * （docs/09_decision_log.md D-36）。
 */
export type UserProfile = {
  id: number;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  postCount: number;
  followingCount: number;
  followerCount: number;
  /** isMe が true の場合は常に false */
  isFollowing: boolean;
  isMe: boolean;
  createdAt: string;
};

/**
 * ユーザー一覧の1行（docs/05_api_design.md 4章 UserListItem）。
 *
 * SC-08（フォロー中一覧）/ SC-09（フォロワー一覧）で共通に使う（docs/09_decision_log.md D-20）。
 */
export type UserListItem = {
  id: number;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  isFollowing: boolean;
  isMe: boolean;
};

/**
 * #19 プロフィール編集のリクエスト。
 *
 * bio は「送らない（変更しない）」と「null を明示的に送る（削除する）」を区別する必要があるため、
 * キー自体を省略できるようオプショナルにする。呼び出し側は「変更しないなら bio キーを含めない
 * オブジェクトを渡す」「削除するなら bio: null を渡す」を使い分ける
 * （docs/05_api_design.md #19、バックエンドの UpdateProfileRequest と同じ区別）。
 */
export type UpdateProfilePayload = {
  displayName?: string;
  bio?: string | null;
};

/** #21 / #22 フォロー・フォロー解除のレスポンス */
export type FollowResponse = {
  isFollowing: boolean;
  followerCount: number;
};
