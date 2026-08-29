package com.example.snstimeline.user.dto;

import com.example.snstimeline.file.dto.UploadFileResponse;
import com.example.snstimeline.user.User;

/**
 * ユーザーの要約表現（docs/05_api_design.md 4章 UserSummary）。
 *
 * <p><b>email は含めない。</b> アカウント列挙を招くため（docs/06_non_functional.md 3.6）。
 *
 * <p>avatarUrl は {@code null} でもキー自体を出す必要があるので、 このrecordに {@code @JsonInclude(NON_NULL)} を付けてはいけない。
 */
public record UserSummary(Long id, String username, String displayName, String avatarUrl) {

  public static UserSummary from(User user) {
    return fromRow(user.id(), user.username(), user.displayName(), user.avatarFileId());
  }

  /** JOIN済みの行（例: 投稿一覧の author_* 列）から組み立てる。 */
  public static UserSummary fromRow(
      Long id, String username, String displayName, Long avatarFileId) {
    String avatarUrl = avatarFileId == null ? null : UploadFileResponse.urlOf(avatarFileId);
    return new UserSummary(id, username, displayName, avatarUrl);
  }
}
