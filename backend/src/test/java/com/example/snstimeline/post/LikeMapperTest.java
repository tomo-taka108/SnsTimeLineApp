package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.annotation.Transactional;

/** {@link LikeMapper} の結合テスト（docs/11_test_design.md 19章、ケース #250〜#256）。 */
@SpringBootTest
@Transactional
class LikeMapperTest extends AbstractIntegrationTest {

  @Autowired private LikeMapper likeMapper;
  @Autowired private TestFixtures fixtures;

  @Nested
  @DisplayName("いいねの登録と解除")
  class InsertDelete {

    /** #250 登録・存在確認・解除の一巡。 */
    @Test
    @DisplayName("#250 insert → exists → delete が一貫して動く")
    void 登録と解除の一巡() {
      long author = fixtures.user("alice");
      long liker = fixtures.user("bob");
      long postId = fixtures.post(author, "投稿");

      assertThat(likeMapper.exists(postId, liker)).isFalse();
      assertThat(likeMapper.insert(postId, liker)).isEqualTo(1);
      assertThat(likeMapper.exists(postId, liker)).isTrue();
      assertThat(likeMapper.delete(postId, liker)).isEqualTo(1);
    }

    /** #251 未いいねの解除は0件（Service層の冪等性ガードが依存している戻り値）。 */
    @Test
    @DisplayName("#251 いいねしていない状態のdeleteは0件")
    void 未いいねの解除は0件() {
      long author = fixtures.user("alice");
      long liker = fixtures.user("bob");
      long postId = fixtures.post(author, "投稿");

      assertThat(likeMapper.delete(postId, liker)).isZero();
    }

    /**
     * #252 <b>UNIQUE制約 {@code uq_likes_post_user} が二重いいねを防ぐ最後の砦。</b>
     *
     * <p>Service層は事前SELECT（D-34）で防いでいるが、同時リクエストではすり抜けうる。 そのときDBが拒否することを確認する。
     *
     * <p>制約違反はトランザクションを中断状態にするため、このテストには他のassertを書かない。
     */
    @Test
    @DisplayName("#252 同じ投稿に二重いいねするとUNIQUE制約違反になる")
    void 二重いいねは制約違反() {
      long author = fixtures.user("alice");
      long liker = fixtures.user("bob");
      long postId = fixtures.post(author, "投稿");
      likeMapper.insert(postId, liker);

      assertThatThrownBy(() -> likeMapper.insert(postId, liker))
          .isInstanceOf(DuplicateKeyException.class);
    }
  }

  @Nested
  @DisplayName("いいね数カウンタ")
  class Counter {

    /** #253 カウンタは相対更新（D-01）。{@code findLikeCount} は非正規化カラムを読む。 */
    @Test
    @DisplayName("#253 incrementLikeCount / decrementLikeCount が増減する")
    void カウンタが増減する() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");

      likeMapper.incrementLikeCount(postId);
      assertThat(likeMapper.findLikeCount(postId)).isEqualTo(1);

      likeMapper.decrementLikeCount(postId);
      assertThat(likeMapper.findLikeCount(postId)).isZero();
    }

    /**
     * #254 {@code ck_posts_like_count (like_count >= 0)}。
     *
     * <p>「いいねしていない状態で解除しても壊れない」（必須項目②）が守られなかった場合の 最終防衛線がここ。制約違反はトランザクションを汚すため、assertは1つだけ。
     */
    @Test
    @DisplayName("#254 いいね数を0から減らすとCHECK制約違反になる")
    void カウンタは負にできない() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");

      assertThatThrownBy(
              () -> {
                likeMapper.decrementLikeCount(postId);
                likeMapper.findLikeCount(postId);
              })
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("一括取得（N+1回避）")
  class BulkLookup {

    /** #255 指定した投稿のうち、自分がいいね済みのものだけを返す。 */
    @Test
    @DisplayName("#255 findLikedPostIdsはいいね済みのIDだけを返す")
    void いいね済みIDの一括取得() {
      long author = fixtures.user("alice");
      long liker = fixtures.user("bob");
      long liked = fixtures.post(author, "いいねした");
      long notLiked = fixtures.post(author, "いいねしていない");
      fixtures.like(liked, liker);

      List<Long> ids = likeMapper.findLikedPostIds(liker, List.of(liked, notLiked));

      assertThat(ids).containsExactly(liked);
    }

    /** #256 空リストは {@code IN ()} になり構文エラー（ガードは {@code PostService.likedPostIdsOf} にある）。 */
    @Test
    @DisplayName("#256 空リストを渡すとSQL構文エラーになる（ガードは呼び出し側の責務）")
    void 空リストは構文エラー() {
      long liker = fixtures.user("bob");

      assertThatThrownBy(() -> likeMapper.findLikedPostIds(liker, List.of()))
          .isInstanceOf(BadSqlGrammarException.class);
    }
  }
}
