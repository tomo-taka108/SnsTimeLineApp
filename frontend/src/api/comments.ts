import { request } from "./client";
import type {
  Comment,
  CreateCommentPayload,
  CreateCommentResponse,
  CursorPage,
  DeleteCommentResponse,
} from "./types";

/**
 * コメントAPIの呼び出し（docs/05_api_design.md #10, #11, #13）。
 *
 * コメント編集（#12）はPhase2のため呼び出し関数を用意しない。
 */

/** #10 コメント一覧。cursor はサーバーが返した不透明な文字列。中身を解釈しない */
export function fetchComments(postId: number, cursor?: string): Promise<CursorPage<Comment>> {
  const params = new URLSearchParams({ limit: "20" });
  if (cursor) {
    params.set("cursor", cursor);
  }
  return request<CursorPage<Comment>>(`/posts/${postId}/comments?${params}`);
}

/** #11 コメント投稿 */
export function createComment(
  postId: number,
  payload: CreateCommentPayload,
): Promise<CreateCommentResponse> {
  return request<CreateCommentResponse>(`/posts/${postId}/comments`, {
    method: "POST",
    body: payload,
  });
}

/** #13 コメント削除。パスに postId を含まない（設計書どおり） */
export function deleteComment(commentId: number): Promise<DeleteCommentResponse> {
  return request<DeleteCommentResponse>(`/comments/${commentId}`, { method: "DELETE" });
}
