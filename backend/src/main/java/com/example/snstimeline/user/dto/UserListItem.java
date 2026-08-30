package com.example.snstimeline.user.dto;

import com.example.snstimeline.file.dto.UploadFileResponse;
import com.example.snstimeline.follow.FollowRow;
import com.example.snstimeline.user.UserSearchRow;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ユーザー一覧の1行（{@code UserSummary} の拡張、docs/05_api_design.md 4章 UserListItem）。
 *
 * <p>SC-07（ユーザー検索, Phase2）/ SC-08（フォロー中一覧）/ SC-09（フォロワー一覧）で共通に使う （docs/09_decision_log.md
 * D-20）。{@code UserSummary} を拡張しないのは、投稿の {@code author} としても使われる {@code UserSummary} に {@code bio}
 * / {@code isFollowing} を足すと、 タイムライン20件ぶんの無駄なフォロー判定が走るため。
 *
 * <p>{@code isFollowing} / {@code isMe} は {@code @JsonProperty} を明示する（{@code PostSummary} の {@code
 * isLikedByMe} と同じ理由）。
 */
public record UserListItem(
    Long id,
    String username,
    String displayName,
    String avatarUrl,
    String bio,
    @JsonProperty("isFollowing") boolean isFollowing,
    @JsonProperty("isMe") boolean isMe) {

  /**
   * フォロー一覧SQLの1行から組み立てる（#23 / #24）。
   *
   * @param isFollowing 呼び出し側が一括取得した「自分がフォロー済みか」の判定結果を渡す （docs/04_data_model.md 6.6、N+1回避）
   */
  public static UserListItem fromFollowRow(FollowRow row, boolean isFollowing, boolean isMe) {
    String avatarUrl =
        row.avatarFileId() == null ? null : UploadFileResponse.urlOf(row.avatarFileId());
    return new UserListItem(
        row.userId(), row.username(), row.displayName(), avatarUrl, row.bio(), isFollowing, isMe);
  }

  /**
   * 検索結果SQLの1行から組み立てる（#20）。
   *
   * <p><b>{@code isMe} は常に false。</b> 検索は自分自身を結果から除外するため （SQLの {@code id <>
   * meId}、docs/04_data_model.md 6.5 要求6）、自分の行は到達しない。
   *
   * @param isFollowing 呼び出し側が一括取得した「自分がフォロー済みか」の判定結果を渡す （docs/04_data_model.md 6.6、N+1回避）
   */
  public static UserListItem fromSearchRow(UserSearchRow row, boolean isFollowing) {
    String avatarUrl =
        row.avatarFileId() == null ? null : UploadFileResponse.urlOf(row.avatarFileId());
    return new UserListItem(
        row.userId(), row.username(), row.displayName(), avatarUrl, row.bio(), isFollowing, false);
  }
}
