package com.example.snstimeline.post;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code likes} テーブルへのアクセス（docs/09_decision_log.md D-25）。 */
@Mapper
public interface LikeMapper {

  /**
   * #14 いいね登録。UNIQUE制約（uq_likes_post_user）違反時は {@link
   * org.springframework.dao.DuplicateKeyException} を LikeService 側で捕捉する（D-34）。
   */
  int insert(@Param("postId") Long postId, @Param("userId") Long userId);

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
