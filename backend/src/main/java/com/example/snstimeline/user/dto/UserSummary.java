package com.example.snstimeline.user.dto;

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
    // avatarUrl は本来 FileStorageService が組み立てる（docs/05_api_design.md 4章）。
    // ファイルモジュールは未実装のため、現時点では常に null を返す。
    // TODO(#5): ファイルモジュール実装時に avatarFileId -> URL 変換を差し込む
    return new UserSummary(user.id(), user.username(), user.displayName(), null);
  }

  /**
   * JOIN済みの行（例: 投稿一覧の author_* 列）から組み立てる。
   *
   * <p>avatarFileId は現時点では使わない（{@link #from(User)} と同じ理由）が、
   * 呼び出し側にJOIN結果の別名だけ持ち回らせることで、URL変換を差し込む場所を ここ1箇所に保つ。
   */
  public static UserSummary fromRow(
      Long id, String username, String displayName, Long avatarFileId) {
    // TODO(#5): ファイルモジュール実装時に avatarFileId -> URL 変換を差し込む
    return new UserSummary(id, username, displayName, null);
  }
}
