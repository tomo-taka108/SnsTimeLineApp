package com.example.snstimeline.comment;

import java.time.OffsetDateTime;

/**
 * コメント一覧SQLの1行（docs/09_decision_log.md D-32と同じ方針）。
 *
 * <p><b>ドメインモデル {@link Comment} とは別物。</b> {@code comments} と {@code users} を JOIN
 * した結果を平坦に受けるための型で、投稿者の情報を含む。{@code resultMap} を使わない理由は {@code PostRow} と同じ（docs/09_decision_log.md
 * D-32）。
 *
 * <p><b>カラムの別名とフィールド名は1対1で対応させること。</b> MyBatis はカラム名の誤りを起動時に検知せず、値が黙って null になる（D-25）。
 */
public record CommentRow(
    Long id,
    String body,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt,
    Long authorId,
    String authorUsername,
    String authorDisplayName,
    Long authorAvatarFileId) {}
