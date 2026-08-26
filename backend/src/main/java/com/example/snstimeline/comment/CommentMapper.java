package com.example.snstimeline.comment;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code comments} テーブルへのアクセス（docs/09_decision_log.md D-25）。 */
@Mapper
public interface CommentMapper {

  /**
   * #10 コメント一覧（docs/05_api_design.md #10）。
   *
   * <p><b>タイムラインと違い古い順（昇順）。</b> カーソル比較も {@code (created_at, id) > cursor} と 不等号の向きが逆になる。 {@code
   * limit} には「取得したい件数 + 1」を渡すこと（PostMapperと同じ判定方法）。
   *
   * @param cursorCreatedAt 初回は null
   * @param cursorId 初回は null
   */
  List<CommentRow> findByPostId(
      @Param("postId") Long postId,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** author情報をJOIN済みの1行を返す（作成直後のレスポンス組み立て用）。 */
  Optional<CommentRow> findRowById(@Param("id") Long id);

  /** 認可判定用。author を JOIN しないぶん軽い。 */
  Optional<Comment> findById(@Param("id") Long id);

  /** record は不変でキーを書き戻せないため RETURNING id で受ける（PostMapper と同じ、D-25）。 */
  Long insert(
      @Param("postId") Long postId, @Param("userId") Long userId, @Param("body") String body);

  /**
   * #13 論理削除。
   *
   * <p>WHERE に user_id を含めない。所有者チェックは Service で先に行い、404 と 403 を出し分ける
   * 必要があるため（docs/09_decision_log.md D-14）。
   */
  int softDelete(@Param("id") Long id);
}
