package com.example.snstimeline.comment;

import java.time.OffsetDateTime;

/**
 * コメント（docs/04_data_model.md 2.3）。
 *
 * <p>{@code comments} テーブルの1行に対応するドメインモデル。認可判定（所有者チェック）用の軽量な型で、 投稿者情報が要る表示用途では {@link CommentRow}
 * を使う。
 */
public record Comment(
    Long id,
    Long postId,
    Long userId,
    String body,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime editedAt,
    OffsetDateTime deletedAt) {}
