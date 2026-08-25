package com.example.snstimeline.post;

import java.time.OffsetDateTime;

/**
 * 投稿（docs/04_data_model.md 2.2）。
 *
 * <p>{@code posts} テーブルの1行に対応するドメインモデル。 タイムライン取得のように投稿者情報も要る場面では、JOIN済みの {@link PostRow} を使う。
 *
 * <p><b>{@code updatedAt} と {@code editedAt} は別物。</b> {@code updatedAt}
 * はカウンタ更新でも変わるため、「ユーザーが本文を編集した」 ことの判定には使えない。UIの「編集済み」表示は {@code editedAt} を見る。
 */
public record Post(
    Long id,
    Long userId,
    String body,
    int likeCount,
    int commentCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime editedAt,
    OffsetDateTime deletedAt) {

  /**
   * 新規投稿用。
   *
   * <p>id・カウンタ・各日時はDB側で採番・設定するため null / 0 を置く（{@code User.forSignup} と同じ方針）。
   */
  public static Post forCreate(Long userId, String body) {
    return new Post(null, userId, body, 0, 0, null, null, null, null);
  }
}
