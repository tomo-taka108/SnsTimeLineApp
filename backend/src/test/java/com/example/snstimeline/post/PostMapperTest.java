package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PostMapper} の結合テスト（docs/11_test_design.md 19章、ケース #223〜#232）。
 *
 * <p>タイムライン系（#204〜#222）は {@link PostMapperTimelineTest} にある。 こちらは行マッピングの完全性・更新系SQL・カウンタを扱う。
 */
@SpringBootTest
@Transactional
class PostMapperTest extends AbstractIntegrationTest {

  @Autowired private PostMapper postMapper;
  @Autowired private TestFixtures fixtures;
  @Autowired private JdbcClient jdbc;

  @Nested
  @DisplayName("行マッピングの完全性")
  class RowMapping {

    /**
     * #223 <b>MyBatisの「黙ってnull」を検出する。</b>
     *
     * <p>MyBatis はカラム名の誤りを起動時に検知しない（JPAの {@code ddl-auto: validate} に相当する 安全網が無い、D-25）。{@code
     * u.display_name AS author_display_name} を {@code author_dispay_name}
     * とタイポしても<b>起動は成功し、Service層のテストも全て緑のまま</b>、 画面の表示名だけが空になる。
     *
     * <p>これを捕まえられるのは<b>実DBに対して全フィールドを検証するこのテストだけ</b>。 そのため非nullチェックではなく、投入した値と1つずつ突き合わせる。
     */
    @Test
    @DisplayName("#223 PostRowの全フィールドが正しくマッピングされる")
    void 全フィールドがマッピングされる() {
      long author = fixtures.user("alice");
      long avatarFileId = fixtures.storedFile(author);
      jdbc.sql("UPDATE users SET avatar_file_id = ? WHERE id = ?")
          .params(avatarFileId, author)
          .update();
      long postId = fixtures.post(author, "本文テスト");
      long liker = fixtures.user("bob");
      fixtures.like(postId, liker);
      fixtures.comment(postId, liker, "コメント");

      PostRow row = postMapper.findRowById(postId).orElseThrow();

      assertThat(row.id()).isEqualTo(postId);
      assertThat(row.userId()).isEqualTo(author);
      assertThat(row.body()).isEqualTo("本文テスト");
      assertThat(row.likeCount()).isEqualTo(1);
      assertThat(row.commentCount()).isEqualTo(1);
      assertThat(row.createdAt()).isNotNull();
      assertThat(row.editedAt()).isNull(); // 未編集なのでnullが正しい
      assertThat(row.authorId()).isEqualTo(author);
      assertThat(row.authorUsername()).isEqualTo("alice");
      assertThat(row.authorDisplayName()).isEqualTo("aliceの表示名");
      assertThat(row.authorAvatarFileId()).isEqualTo(avatarFileId);
    }

    /** #224 編集済みの投稿では {@code editedAt} が入る（#223で null を確認した対の関係）。 */
    @Test
    @DisplayName("#224 編集済みの投稿では editedAt が入る")
    void 編集済みならeditedAtが入る() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "編集前");

      postMapper.updateBody(postId, "編集後");

      PostRow row = postMapper.findRowById(postId).orElseThrow();
      assertThat(row.body()).isEqualTo("編集後");
      assertThat(row.editedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("更新系SQL")
  class Updates {

    /**
     * #225 {@code softDelete} は既に削除済みなら0件を返す。
     *
     * <p>{@code PostService.delete} はこの戻り値0を「競合（他の誰かが先に削除した）」の判定に使っている。 {@code AND deleted_at IS
     * NULL} が外れると二重削除が成功し、Controller層では 2回目のDELETEが404ではなく204を返すようになる（#279と対）。
     */
    @Test
    @DisplayName("#225 削除済みの投稿をもう一度softDeleteすると0件")
    void 二重削除は0件() {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");

      assertThat(postMapper.softDelete(postId)).isEqualTo(1);
      assertThat(postMapper.softDelete(postId)).isZero();
    }

    /** #226 削除済みの投稿は本文を更新できない。 */
    @Test
    @DisplayName("#226 削除済みの投稿は updateBody で更新できない")
    void 削除済みは更新できない() {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");
      fixtures.softDeletePost(postId);

      assertThat(postMapper.updateBody(postId, "更新後")).isZero();
    }

    /**
     * #227 {@code softDelete} は {@code edited_at} を触らない。
     *
     * <p>削除したことで「編集済み」バッジが付いてはいけない（04_data_model.md 2.2）。
     *
     * <p>なお {@code @Transactional} 内では {@code now()} が固定されるため、 「{@code edited_at > created_at}」という
     * assert は成立しない。null かどうかで判定する。
     */
    @Test
    @DisplayName("#227 softDeleteは editedAt を設定しない")
    void 削除では編集済みにしない() {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");

      postMapper.softDelete(postId);

      var editedAt =
          jdbc.sql("SELECT edited_at FROM posts WHERE id = ?")
              .param(postId)
              .query(java.time.OffsetDateTime.class)
              .optional();
      assertThat(editedAt).isEmpty();
    }
  }

  @Nested
  @DisplayName("コメント数カウンタ")
  class CommentCounter {

    /** #228 増減が相対更新で効くこと（D-01）。 */
    @Test
    @DisplayName("#228 incrementCommentCount / decrementCommentCount が増減する")
    void カウンタが増減する() {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");

      postMapper.incrementCommentCount(postId);
      assertThat(fixtures.commentCountOf(postId)).isEqualTo(1);

      postMapper.decrementCommentCount(postId);
      assertThat(fixtures.commentCountOf(postId)).isZero();
    }

    /**
     * #229 <b>DBのCHECK制約が最後の砦であることの確認。</b>
     *
     * <p>{@code ck_posts_comment_count (comment_count >= 0)}。カウンタが負になる経路が
     * 万一Service層をすり抜けても、DBが拒否する。
     *
     * <p><b>このテストには制約違反のassertを1つしか書かない。</b> PostgreSQLは文がエラーになると トランザクション全体が中断状態になり、以降の文がすべて
     * {@code current transaction is aborted} で失敗するため（D-34と同じ理由）。
     */
    @Test
    @DisplayName("#229 コメント数を0から減らすとCHECK制約違反になる")
    void カウンタは負にできない() {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");

      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> {
                postMapper.decrementCommentCount(postId);
                // MyBatisは実行を遅延しないが、制約違反はflush時に出るため明示的に読み出す
                fixtures.commentCountOf(postId);
              })
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }

  @Nested
  @DisplayName("論理削除の扱いが list と count で異なる箇所（Issue #44）")
  class SoftDeleteInconsistency {

    /**
     * #230 <b>現状の挙動を固定している。本来は list と count が一致すべき（Issue #44 ①）。</b>
     *
     * <p>{@code findTimeline} は users とJOINして退会者の投稿を除くが、{@code countNewer} は
     * postsしか見ないため退会者の投稿を数えてしまう。利用者から見ると「新着1件」と出るのに 押しても何も無い。
     *
     * <p><b>修正時はこのテストの期待値を反転させること</b>（countNewer も 0 になるべき）。
     */
    @Test
    @DisplayName("#230 countNewerは退会ユーザーの投稿を数えるが、findTimelineは返さない（Issue #44）")
    void 新着件数と一覧が食い違う() {
      long me = fixtures.user("alice");
      long gone = fixtures.user("bob");
      long base = fixtures.post(me, "基準");
      fixtures.post(gone, "退会者の新しい投稿");
      fixtures.softDeleteUser(gone);

      long newCount = postMapper.countNewer(TimelineTab.ALL, me, base);
      List<PostRow> rows = postMapper.findTimeline(TimelineTab.ALL, me, null, null, 100);

      // 現状: カウントは1、一覧は0件（基準の投稿より新しいものは無い）
      assertThat(newCount).isEqualTo(1);
      assertThat(rows).extracting(PostRow::id).containsExactly(base);
    }
  }
}
