package com.example.snstimeline.user;

/**
 * ユーザー検索SQLの1行（docs/05_api_design.md #20）。
 *
 * <p>{@code FollowRow} / {@code PostRow} と同じ方針で {@code <resultMap>} は使わない （docs/09_decision_log.md
 * D-32）。カラムの別名とフィールド名は1対1で対応させること （MyBatis はカラム名の誤りを起動時に検知せず、値が黙って null になる、D-25）。
 *
 * <p>{@link User} をそのまま使わない理由: {@code User} は {@code email} と {@code passwordHash} を持つ。
 * <b>検索結果に認証情報を載せないため、必要な列だけの専用の行型にする</b>（docs/04_data_model.md 6.5 要求2、CLAUDE.md 6章）。
 *
 * <p>並び替えに使う {@code similarity()} の値は SELECT に含めない。 呼び出し側は SQL が返した順序をそのまま使うため、スコアを Java 側で持つ必要がない。
 */
public record UserSearchRow(
    Long userId, String username, String displayName, String bio, Long avatarFileId) {}
