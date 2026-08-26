package com.example.snstimeline.user.dto;

import com.example.snstimeline.user.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * プロフィール画面用のユーザー表現（docs/05_api_design.md 4章 UserProfile）。
 *
 * <p>{@code UserSummary} を拡張せず独立した型にする（docs/09_decision_log.md D-20）。 {@code UserSummary} は投稿の
 * {@code author} としても使われるため、そこに {@code postCount} 等を足すとタイムライン20件ぶんの余計な集計が走ってしまう。
 *
 * <p><b>{@code email} は含めない。</b> アカウント列挙を招くため（docs/06_non_functional.md 3.6）。
 *
 * <p>{@code postCount} / {@code followingCount} / {@code followerCount} は非正規化カウンタを 持たず都度 {@code
 * COUNT(*)} で算出する（docs/09_decision_log.md D-36）。
 *
 * <p>{@code isFollowing} / {@code isMe} は {@code @JsonProperty} を明示する（{@code PostSummary} の {@code
 * isLikedByMe} と同じ理由）。
 */
public record UserProfile(
    Long id,
    String username,
    String displayName,
    String avatarUrl,
    String bio,
    int postCount,
    int followingCount,
    int followerCount,
    @JsonProperty("isFollowing") boolean isFollowing,
    @JsonProperty("isMe") boolean isMe,
    OffsetDateTime createdAt) {

  /**
   * @param isFollowing isMe が true の場合は常に false にすること（呼び出し側の責務、 docs/05_api_design.md 4章
   *     UserProfile の isFollowing の説明）
   */
  public static UserProfile of(
      User user,
      int postCount,
      int followingCount,
      int followerCount,
      boolean isFollowing,
      boolean isMe) {
    // avatarUrl は本来 FileStorageService が組み立てる。
    // ファイルモジュールは未実装のため、現時点では常に null を返す（UserSummary と同じ理由）。
    // TODO(#5): ファイルモジュール実装時に avatarFileId -> URL 変換を差し込む
    return new UserProfile(
        user.id(),
        user.username(),
        user.displayName(),
        null,
        user.bio(),
        postCount,
        followingCount,
        followerCount,
        isFollowing,
        isMe,
        user.createdAt());
  }
}
