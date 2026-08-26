package com.example.snstimeline.comment.dto;

/**
 * #11 コメント投稿のレスポンス（docs/05_api_design.md #11）。
 *
 * <p>{@code commentCount} を返すことで、クライアントが投稿カードのコメント数を再取得なしで即座に更新できる。
 */
public record CreateCommentResponse(CommentSummary comment, int commentCount) {}
