package com.example.snstimeline.user;

import java.time.OffsetDateTime;

/**
 * users テーブルに対応するドメインモデル（docs/04_data_model.md 2.1）。
 *
 * <p>MyBatis はプロキシを必要としないため record で書ける（JPA の @Entity と違い不変にできる）。
 *
 * <p><b>email と passwordHash は絶対にAPIレスポンスへ出さない。</b> 外に出すときは必ず {@link
 * com.example.snstimeline.user.dto.UserSummary} に詰め替える。
 */
public record User(
    Long id,
    String email,
    String passwordHash,
    String username,
    String displayName,
    String bio,
    Long avatarFileId,
    Long coverFileId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    OffsetDateTime deletedAt) {

  /** 新規登録用。id と各日時はDB側で採番・設定する。 */
  public static User forSignup(
      String email, String passwordHash, String username, String displayName) {
    return new User(
        null, email, passwordHash, username, displayName, null, null, null, null, null, null);
  }
}
