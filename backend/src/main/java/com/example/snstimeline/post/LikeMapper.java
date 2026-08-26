package com.example.snstimeline.post;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code likes} テーブルへのアクセス（docs/09_decision_log.md D-25）。 */
@Mapper
public interface LikeMapper {

  /**
   * #14 いいね登録。
   *
   * <p>呼び出し前に {@link #exists} で存在確認し、TOCTOU（確認後の競合）はDBのUNIQUE制約
   * （uq_likes_post_user）に委ねる。PostgreSQLは制約違反が起きたトランザクションを中断状態にし、
   * Java側で例外を捕捉しても同一トランザクション内の以降の文がすべて失敗するため、事前確認で 重複をほぼ防いだ上で使う（docs/09_decision_log.md D-34）。
   */
  int insert(@Param("postId") Long postId, @Param("userId") Long userId);

  /** 既にいいね済みかどうか（冪等性の判定用）。 */
  boolean exists(@Param("postId") Long postId, @Param("userId") Long userId);

  /** #15 いいね解除。物理削除（docs/09_decision_log.md D-02）。 */
  int delete(@Param("postId") Long postId, @Param("userId") Long userId);

  /** カウンタの相対更新（D-01）。posts の存在チェックは呼び出し元（LikeService）が先に済ませる。 */
  int incrementLikeCount(@Param("postId") Long postId);

  int decrementLikeCount(@Param("postId") Long postId);

  /** 更新後の実カウントを引き直す（#14 / #15 のレスポンス用）。 */
  int findLikeCount(@Param("postId") Long postId);

  /**
   * 自分がいいね済みの投稿IDを一括取得する（docs/04_data_model.md 5.3、N+1回避）。
   *
   * <p>タイムライン・投稿詳細のどちらでも、投稿1件ごとに問い合わせず、 表示する投稿ID群に対して1回のクエリで済ませること。
   *
   * @param postIds 空リストの場合は呼び出さないこと（Service側でガードする）
   */
  List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") List<Long> postIds);
}
