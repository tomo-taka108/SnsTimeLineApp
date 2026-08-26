package com.example.snstimeline.comment.dto;

import com.example.snstimeline.comment.CommentRow;
import com.example.snstimeline.user.dto.UserSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * コメント（docs/05_api_design.md 4章 Comment）。
 *
 * <p>{@code editedAt} はコメント編集（F-CM-03）がPhase2のため今回は常に {@code null}。
 *
 * <p>{@code isMine} は {@code @JsonProperty} を明示する。{@code PostSummary.isLikedByMe} と同じ理由 （record +
 * boolean アクセサの組み合わせがJacksonのバージョンによって揺れうるため）。
 */
public record CommentSummary(
    Long id,
    UserSummary author,
    String body,
    @JsonProperty("isMine") boolean isMine,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt) {

  /** JOIN済みの行と、認可判定に使った自分のユーザーIDから組み立てる。 */
  public static CommentSummary from(CommentRow row, Long meId) {
    UserSummary author =
        UserSummary.fromRow(
            row.authorId(),
            row.authorUsername(),
            row.authorDisplayName(),
            row.authorAvatarFileId());
    return new CommentSummary(
        row.id(), author, row.body(), row.authorId().equals(meId), row.createdAt(), row.editedAt());
  }
}
