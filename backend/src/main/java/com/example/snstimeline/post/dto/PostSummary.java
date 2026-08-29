package com.example.snstimeline.post.dto;

import com.example.snstimeline.post.PostRow;
import com.example.snstimeline.user.dto.UserSummary;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 投稿（タイムライン・詳細で共通、docs/05_api_design.md 4章 PostSummary）。
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

  /**
   * タイムラインSQLの1行から組み立てる。
   *
   * @param isLikedByMe 呼び出し側が一括取得した「自分がいいね済みか」の判定結果を渡す （docs/04_data_model.md 5.3、N+1回避）
   * @param images 同じく呼び出し側が一括取得した添付画像（docs/09_decision_log.md D-45）。 空なら {@code List.of()}
   *     を渡すこと（{@code null} にしない）
   */
  public static PostSummary from(PostRow row, boolean isLikedByMe, List<PostImageSummary> images) {
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
        images,
        row.likeCount(),
        row.commentCount(),
        isLikedByMe,
        row.createdAt(),
        row.editedAt());
  }
}
