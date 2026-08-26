package com.example.snstimeline.post;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code posts} テーブルへのアクセス（docs/09_decision_log.md D-25）。 */
@Mapper
public interface PostMapper {

  /**
   * タイムライン（docs/04_data_model.md 5.1 / 5.2、API #5）。
   *
   * <p>{@code limit} には「取得したい件数 + 1」を渡すこと。1件多く返ってきたら次ページがある という判定に使う（COUNT(*)
   * を別途投げずに済ませるため、docs/09_decision_log.md D-06）。
   *
   * @param cursorCreatedAt 初回は null（2ページ目以降の絞り込みに使う）
   * @param cursorId 初回は null
   */
  List<PostRow> findTimeline(
      @Param("tab") TimelineTab tab,
      @Param("meId") Long meId,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** #29 新着件数。sinceId より新しい投稿を数える。 */
  long countNewer(
      @Param("tab") TimelineTab tab, @Param("meId") Long meId, @Param("sinceId") Long sinceId);

  /** author情報をJOIN済みの1行を返す（作成直後のレスポンス組み立て用）。 */
  Optional<PostRow> findRowById(@Param("id") Long id);

  /** 認可判定用。author を JOIN しないぶん軽い。 */
  Optional<Post> findById(@Param("id") Long id);

  /** record は不変でキーを書き戻せないため RETURNING id で受ける（UserMapper と同じ、D-25）。 */
  Long insert(@Param("userId") Long userId, @Param("body") String body);

  /**
   * 本文編集（#8）。
   *
   * <p>WHERE に user_id を含めない。所有者チェックは Service で先に行い、404 と 403 を
   * 出し分ける必要があるため（docs/09_decision_log.md D-14）。戻り値（affected rows）が 0 なら、 判定と更新の間に削除された競合とみなす。
   */
  int updateBody(@Param("id") Long id, @Param("body") String body);

  /** #9 論理削除。updateBody と同じ理由で WHERE に user_id を含めない。 */
  int softDelete(@Param("id") Long id);

  /**
   * コメント登録時のカウンタ更新（D-01）。{@code comment_count} は posts の列なので、 comments テーブルの担当である CommentMapper
   * ではなくここに置く。
   */
  int incrementCommentCount(@Param("id") Long id);

  /** コメント削除時のカウンタ更新。CommentService から同一トランザクションで呼ばれる。 */
  int decrementCommentCount(@Param("id") Long id);

  /** 更新後の実カウントを引き直す（#11 / #13 のレスポンス用）。 */
  int findCommentCount(@Param("id") Long id);
}
