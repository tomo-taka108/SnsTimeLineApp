package com.example.snstimeline.post.dto;

import com.example.snstimeline.post.PostRow;
import com.example.snstimeline.user.dto.UserSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 投稿（タイムライン・詳細で共通、docs/05_api_design.md 4章 PostSummary）。
 *
 * <p>{@code images} は画像機能未実装のため常に空配列（{@code null} にしない）。
 *
 * <p>{@code likeCount} / {@code isLikedByMe} は今回いいねAPI（#14/#15）を実装しないため、 常に {@code 0} / {@code
 * false} を返す。カード側は表示のみで反応しない。
 *
 * <p><b>{@code isLikedByMe} は {@code @JsonProperty} を明示する。</b> record + boolean
 * アクセサの組み合わせはJacksonのバージョンによって {@code likedByMe} と解釈されうるため、確実にAPI契約どおりの キー名で出す。
 *
 * <p>{@code impressionCount} のようなフィールドは存在しない。インプレッション数を表示しないという 差別化ポイント（docs/01_requirements.md
 * 2.2）はAPIレベルでも徹底する。
 */
public record PostSummary(
    Long id,
    UserSummary author,
    String body,
    List<PostImageSummary> images,
    int likeCount,
    int commentCount,
    @JsonProperty("isLikedByMe") boolean isLikedByMe,
    OffsetDateTime createdAt,
    OffsetDateTime editedAt) {

  /** タイムラインSQLの1行から組み立てる。いいね・画像は今回未実装のため固定値になる。 */
  public static PostSummary from(PostRow row) {
    UserSummary author =
        UserSummary.fromRow(
            row.authorId(),
            row.authorUsername(),
            row.authorDisplayName(),
            row.authorAvatarFileId());
    return new PostSummary(
        row.id(),
        author,
        row.body(),
        List.of(),
        row.likeCount(),
        row.commentCount(),
        false,
        row.createdAt(),
        row.editedAt());
  }
}
