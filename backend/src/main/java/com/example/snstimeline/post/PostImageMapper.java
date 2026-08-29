package com.example.snstimeline.post;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** {@code post_images} テーブルへのアクセス（docs/09_decision_log.md D-25）。 */
@Mapper
public interface PostImageMapper {

  /** #6 投稿作成時の添付。MVPは {@code displayOrder=0} の1件のみ呼ばれる。 */
  void insert(
      @Param("postId") Long postId,
      @Param("fileId") Long fileId,
      @Param("displayOrder") int displayOrder);

  /**
   * 投稿ID群に対する添付画像の一括取得（N+1回避、docs/09_decision_log.md D-45）。
   *
   * <p>タイムライン・投稿一覧・投稿詳細のどれでも、投稿1件ごとに問い合わせず、 表示する投稿ID群に対して1回のクエリで済ませること。
   *
   * @param postIds 空リストの場合は呼び出さないこと（Service側でガードする）
   */
  List<PostImageRow> findByPostIds(@Param("postIds") List<Long> postIds);
}
