package com.example.snstimeline.comment.dto;

/**
 * #13 コメント削除のレスポンス（docs/05_api_design.md #13）。
 *
 * <p>204ではなく200を返すのは、更新後の {@code commentCount} をクライアントに返す必要があるため。
 */
public record DeleteCommentResponse(int commentCount) {}
