package com.example.snstimeline.follow;

import java.time.OffsetDateTime;

/**
 * フォロー一覧SQLの1行（{@code follows} と {@code users} をJOINしたフラット行、docs/09_decision_log.md D-32）。
 *
 * <p>{@code PostRow} と同じ方針で {@code <resultMap>} は使わない（理由は {@code PostRow} のJavadoc参照）。
 * カラムの別名とフィールド名は1対1で対応させること（MyBatis はカラム名の誤りを起動時に検知せず、値が黙って null になる、D-25）。
 *
 * <p>{@code followCreatedAt} はソートキー（カーソル用）。{@code follows.created_at} であり、 ユーザーの登録日時（{@code
 * userCreatedAt}）とは別物なので名前を分けている。
 */
public record FollowRow(
    Long followId,
    OffsetDateTime followCreatedAt,
    Long userId,
    String username,
    String displayName,
    String bio,
    Long avatarFileId,
    OffsetDateTime userCreatedAt) {}
