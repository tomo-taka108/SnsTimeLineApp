import { request } from "./client";
import type {
  CreatePostPayload,
  CursorPage,
  LikeResponse,
  NewCountResponse,
  PostSummary,
  TimelineTab,
  UpdatePostPayload,
} from "./types";

/**
 * 投稿・タイムラインAPIの呼び出し（docs/05_api_design.md #5〜#9, #14, #15、および独自追加の #29）。
 */

/** #5 タイムライン取得。cursor はサーバーが返した不透明な文字列。中身を解釈しない */
export function fetchTimeline(tab: TimelineTab, cursor?: string): Promise<CursorPage<PostSummary>> {
  const params = new URLSearchParams({ tab, limit: "20" });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return request<CursorPage<PostSummary>>(`/timeline?${params}`);
}

/**
 * #29 新着投稿の件数（設計書#1〜#28には無い独自API、docs/09_decision_log.md D-31）。
 *
 * sinceId は現在表示している先頭投稿の id。カーソルは「末尾」しか表さないため使えない。
 */
export function fetchNewCount(tab: TimelineTab, sinceId: number): Promise<NewCountResponse> {
  const params = new URLSearchParams({ tab, sinceId: String(sinceId) });
  return request<NewCountResponse>(`/timeline/new-count?${params}`);
}

/** #6 投稿作成。成功すると 201 で作成された投稿が返る */
export function createPost(payload: CreatePostPayload): Promise<PostSummary> {
  return request<PostSummary>("/posts", { method: "POST", body: payload });
}

/** #7 投稿詳細取得 */
export function fetchPost(postId: number): Promise<PostSummary> {
  return request<PostSummary>(`/posts/${postId}`);
}

/**
 * #8 投稿編集（docs上はPhase2だが、今回MVPへ前倒しした。docs/09_decision_log.md D-30）。
 * 画像は変更できない。
 */
export function updatePost(postId: number, payload: UpdatePostPayload): Promise<PostSummary> {
  return request<PostSummary>(`/posts/${postId}`, { method: "PATCH", body: payload });
}

/** #9 投稿削除。204 が返る */
export function deletePost(postId: number): Promise<void> {
  return request<void>(`/posts/${postId}`, { method: "DELETE" });
}

/** #14 いいね。冪等 */
export function likePost(postId: number): Promise<LikeResponse> {
  return request<LikeResponse>(`/posts/${postId}/like`, { method: "PUT" });
}

/** #15 いいね解除。冪等 */
export function unlikePost(postId: number): Promise<LikeResponse> {
  return request<LikeResponse>(`/posts/${postId}/like`, { method: "DELETE" });
}
