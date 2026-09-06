package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** {@link PostImageMapper} の結合テスト（docs/11_test_design.md 19章、ケース #272〜#275）。 */
@SpringBootTest
@Transactional
class PostImageMapperTest extends AbstractIntegrationTest {

  @Autowired private PostImageMapper postImageMapper;
  @Autowired private TestFixtures fixtures;
  @Autowired private JdbcClient jdbc;

  /** 画像の縦横は {@code stored_files} 側に入る。JOINで取れることを確認するために設定する。 */
  private long imageWithSize(long uploadedBy, int width, int height) {
    long fileId = fixtures.storedFile(uploadedBy);
    jdbc.sql("UPDATE stored_files SET width = ?, height = ? WHERE id = ?")
        .params(width, height, fileId)
        .update();
    return fileId;
  }

  /** #272 複数投稿ぶんを一括取得し、投稿ごとにまとまり、{@code display_order} 順に並ぶこと。 */
  @Test
  @DisplayName("#272 複数投稿の画像が post_id・display_order 順で返る")
  void 一括取得の順序() {
    long me = fixtures.user("alice");
    long post1 = fixtures.post(me, "投稿1");
    long post2 = fixtures.post(me, "投稿2");
    long f1 = imageWithSize(me, 800, 600);
    long f2 = imageWithSize(me, 640, 480);
    long f3 = imageWithSize(me, 100, 100);

    // わざと display_order の逆順で登録し、SQLのORDER BYが効いていることを確かめる
    postImageMapper.insert(post1, f2, 1);
    postImageMapper.insert(post1, f1, 0);
    postImageMapper.insert(post2, f3, 0);

    List<PostImageRow> rows = postImageMapper.findByPostIds(List.of(post1, post2));

    assertThat(rows).extracting(PostImageRow::fileId).containsExactly(f1, f2, f3);
    assertThat(rows).extracting(PostImageRow::postId).containsExactly(post1, post1, post2);
  }

  /**
   * #273 {@code width} / {@code height} が {@code stored_files} とのJOINで取れること。
   *
   * <p>これが null になるとフロントでレイアウトシフトが起きる（06_non_functional.md 1.3）。
   */
  @Test
  @DisplayName("#273 width / height が stored_files から取れる")
  void 縦横が取れる() {
    long me = fixtures.user("alice");
    long postId = fixtures.post(me, "投稿");
    long fileId = imageWithSize(me, 1200, 900);
    postImageMapper.insert(postId, fileId, 0);

    List<PostImageRow> rows = postImageMapper.findByPostIds(List.of(postId));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).width()).isEqualTo(1200);
    assertThat(rows.get(0).height()).isEqualTo(900);
  }

  /**
   * #274 {@code post_images} には {@code deleted_at} が無く、投稿を論理削除しても画像の紐付けは残る（V7 / R-03）。
   *
   * <p>復元可能性のための仕様。一覧に出さないのは投稿側のSQLの責務。
   */
  @Test
  @DisplayName("#274 投稿を論理削除しても画像の紐付けは残る（仕様）")
  void 投稿削除でも画像の紐付けは残る() {
    long me = fixtures.user("alice");
    long postId = fixtures.post(me, "投稿");
    long fileId = imageWithSize(me, 800, 600);
    postImageMapper.insert(postId, fileId, 0);
    fixtures.softDeletePost(postId);

    List<PostImageRow> rows = postImageMapper.findByPostIds(List.of(postId));

    assertThat(rows).hasSize(1);
  }

  /** #275 空リストは {@code IN ()} になり構文エラー（ガードは {@code PostService.imagesOf} にある）。 */
  @Test
  @DisplayName("#275 空リストを渡すとSQL構文エラーになる（ガードは呼び出し側の責務）")
  void 空リストは構文エラー() {
    assertThatThrownBy(() -> postImageMapper.findByPostIds(List.of()))
        .isInstanceOf(BadSqlGrammarException.class);
  }
}
