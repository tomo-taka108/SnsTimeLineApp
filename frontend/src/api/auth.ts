import { request } from "./client";
import type { AuthResponse, LoginPayload, SignupPayload, UserSummary } from "./types";

/**
 * 認証APIの呼び出し（docs/05_api_design.md #1〜#3, #27, #28）。
 *
 * public: true を付けたものは認証不要のエンドポイント。
 * これらの401は「認証情報が違う」意味なので、トークン再発行の対象にしない。
 */

/** #1 新規登録。成功すると 201 でトークンが返る（そのままログイン状態になる） */
export function signup(payload: SignupPayload): Promise<AuthResponse> {
  return request<AuthResponse>("/auth/signup", { method: "POST", body: payload, public: true });
}

/** #2 ログイン。401 は INVALID_CREDENTIALS のみ */
export function login(payload: LoginPayload): Promise<AuthResponse> {
  return request<AuthResponse>("/auth/login", { method: "POST", body: payload, public: true });
}

/** #3 現在のユーザー。起動時に認証状態を復元するために使う */
export function fetchMe(): Promise<UserSummary> {
  return request<UserSummary>("/auth/me");
}

/**
 * #28 ログアウト。204 が返る。
 *
 * サーバー側でリフレッシュトークンを失効させる。
 * 発行済みのアクセストークンは失効しない（最大15分有効なまま）ため、
 * クライアント側でも必ず両トークンを削除すること。
 */
export function logout(): Promise<void> {
  return request<void>("/auth/logout", { method: "POST" });
}
