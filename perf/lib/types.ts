/**
 * 負荷試験が読むレスポンスの最小型定義（docs/13_performance_test.md）。
 *
 * 【なぜ frontend の型を再利用しないのか】
 * k6 は Node.js のモジュール解決アルゴリズムをサポートしていない
 * （公式ドキュメント明記）。require() は「k6 の組み込みモジュール・
 * ローカルファイル・HTTP(S) 越しのスクリプト」しか読めないため、
 * frontend/src の型を import することは技術的に不可能。
 *
 * 重複にはなるが、代わりに「フロントの型を負荷試験の都合で変更しなくて済む」
 * という利点がある。意図的な分離（D-58 の射程外）。
 *
 * ここには**負荷試験が実際に読む項目だけ**を書く。
 * API の全項目を写経すると、フロント側の変更に追随する負担が生まれる。
 */

/** POST /auth/login のリクエスト */
export interface LoginRequest {
  email: string;
  password: string;
}

/** POST /auth/login のレスポンス（user は読まないので省略） */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/** タイムラインの1件。id 以外は読まないので省略 */
export interface PostSummary {
  id: number;
  body: string;
  likeCount: number;
  commentCount: number;
}

/**
 * カーソルページネーションのレスポンス（docs/05_api_design.md 2.1）。
 *
 * nextCursor は**不透明な文字列**として扱うこと。
 * 中身（Base64 された {"c":"<時刻>","i":<id>}）を解釈してはならない、
 * という規約が API 設計にある。次のリクエストにそのまま渡すだけでよい。
 */
export interface CursorPage<T> {
  items: T[];
  nextCursor: string | null;
  hasNext: boolean;
}
