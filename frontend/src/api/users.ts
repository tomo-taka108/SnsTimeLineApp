import { request } from "./client";
import type {
  CursorPage,
  FollowResponse,
  OffsetPage,
  PostSummary,
  UpdateProfilePayload,
  UserListItem,
  UserProfile,
} from "./types";

/**
 * ユーザー・プロフィール・検索・フォローAPIの呼び出し（docs/05_api_design.md #17〜#24）。
 */

/**
 * #20 ユーザー検索。<b>唯一のオフセットページネーション</b>（docs/05_api_design.md 2.2）。
 *
 * <b>q が空のまま呼ばないこと。</b> サーバーは400を返す。未入力時は呼び出し自体を抑止する
 * （docs/03_screen_design.md SC-07）。
 *
 * @param signal 入力が変わったときに古いリクエストを中断するための AbortSignal。
 *   中断されると AbortError を投げるので、呼び出し側で握りつぶす
 */
export function searchUsers(
  q: string,
  page = 0,
  signal?: AbortSignal,
): Promise<OffsetPage<UserListItem>> {
  const params = new URLSearchParams({ q, page: String(page) });
  return request<OffsetPage<UserListItem>>(`/users?${params}`, { signal });
}

/** #17 プロフィール取得 */
export function fetchProfile(userId: number): Promise<UserProfile> {
  return request<UserProfile>(`/users/${userId}`);
}

/** #18 ユーザーの投稿一覧 */
export function fetchUserPosts(userId: number, cursor?: string): Promise<CursorPage<PostSummary>> {
  const params = new URLSearchParams({ limit: "20" });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return request<CursorPage<PostSummary>>(`/users/${userId}/posts?${params}`);
}

/**
 * #19 プロフィール編集。
 *
 * payload に含めたキーだけが更新される。bio を削除したい場合は `{ bio: null }` を渡す
 * （JSON.stringify は undefined のキーを落とし、null のキーは残すため、この関数の呼び出し側は
 * 「変更しないフィールドはオブジェクトに含めない」だけを意識すればよい）。
 */
export function updateProfile(payload: UpdateProfilePayload): Promise<UserProfile> {
  return request<UserProfile>("/users/me", { method: "PATCH", body: payload });
}

/** #21 フォロー。冪等 */
export function followUser(userId: number): Promise<FollowResponse> {
  return request<FollowResponse>(`/users/${userId}/follow`, { method: "PUT" });
}

/** #22 フォロー解除。冪等 */
export function unfollowUser(userId: number): Promise<FollowResponse> {
  return request<FollowResponse>(`/users/${userId}/follow`, { method: "DELETE" });
}

/** #23 フォロー中一覧 */
export function fetchFollowing(userId: number, cursor?: string): Promise<CursorPage<UserListItem>> {
  const params = new URLSearchParams({ limit: "20" });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return request<CursorPage<UserListItem>>(`/users/${userId}/following?${params}`);
}

/** #24 フォロワー一覧 */
export function fetchFollowers(userId: number, cursor?: string): Promise<CursorPage<UserListItem>> {
  const params = new URLSearchParams({ limit: "20" });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return request<CursorPage<UserListItem>>(`/users/${userId}/followers?${params}`);
}
