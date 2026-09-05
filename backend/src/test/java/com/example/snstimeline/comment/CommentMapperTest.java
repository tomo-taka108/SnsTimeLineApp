package com.example.snstimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.snstimeline.common.CursorCodec;
import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CommentMapper} の結合テスト（docs/11_test_design.md 19章、ケース #233〜#241）。
 *
 * <p><b>コメント一覧のカーソルは投稿とは逆向き。</b> 投稿が {@code ORDER BY created_at DESC, id DESC} ＋ {@code <}
 * なのに対し、コメントは {@code ASC, ASC} ＋ {@code >} を使う（古い順に読むため）。 PostMapper からのコピーで向きだけ直し忘れると、2ページ目が常に空になる
 * —— 本プロジェクトで最も壊れやすいカーソルSQLであり、#233 はそれを狙ったテスト。
 */
@SpringBootTest
@Transactional
class CommentMapperTest extends AbstractIntegrationTest {

  @Autowired private CommentMapper commentMapper;
  @Autowired private TestFixtures fixtures;
  @Autowired private JdbcClient jdbc;

  private static final int NO_LIMIT = 100;

  @Nested
  @DisplayName("カーソル（昇順）")
  class Cursor {

    /**
     * #233 <b>昇順カーソルのタイブレーカー。</b>
     *
     * <p>同一 {@code created_at} のコメントが2件あっても、取りこぼしも重複も起きないこと。 投稿側（#212）と違い<b>古い順</b>に並ぶため、1件目は id
     * の小さい方になる。
     */
    @Test
    @DisplayName("#233 同一created_atのコメント2件が重複も欠落もなくページングできる")
    void 同時刻2件がページングできる() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      OffsetDateTime t = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
      long c1 = fixtures.commentAt(postId, author, "同時刻A", t);
      long c2 = fixtures.commentAt(postId, author, "同時刻B", t);

      // 昇順なので c1 が先
      List<CommentRow> page1 = commentMapper.findByPostId(postId, null, null, 2);
      assertThat(page1).extracting(CommentRow::id).containsExactly(c1, c2);

      CommentRow last = page1.get(0);
      CursorCodec.Cursor cursor =
          CursorCodec.decode(CursorCodec.encode(last.createdAt(), last.id()));
      List<CommentRow> page2 =
          commentMapper.findByPostId(postId, cursor.createdAt(), cursor.id(), 2);

      assertThat(page2).extracting(CommentRow::id).containsExactly(c2);
    }

    /** #234 時刻が混在しても古い順（created_at ASC, id ASC）に並ぶこと。 */
    @Test
    @DisplayName("#234 コメントは古い順に並ぶ")
    void コメントは古い順() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      OffsetDateTime t = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
      long newer = fixtures.commentAt(postId, author, "新しい", t.plusSeconds(1));
      long older = fixtures.commentAt(postId, author, "古い", t.minusSeconds(1));
      long middle = fixtures.commentAt(postId, author, "中間", t);

      List<CommentRow> rows = commentMapper.findByPostId(postId, null, null, NO_LIMIT);

      assertThat(rows).extracting(CommentRow::id).containsExactly(older, middle, newer);
    }
  }

  @Nested
  @DisplayName("行マッピングの完全性")
  class RowMapping {

    /** #235 {@code CommentRow} の全8フィールドを投入値と突き合わせる（#223と同じ狙い）。 */
    @Test
    @DisplayName("#235 CommentRowの全フィールドが正しくマッピングされる")
    void 全フィールドがマッピングされる() {
      long author = fixtures.user("alice");
      long avatarFileId = fixtures.storedFile(author);
      jdbc.sql("UPDATE users SET avatar_file_id = ? WHERE id = ?")
          .params(avatarFileId, author)
          .update();
      long postId = fixtures.post(author, "投稿");
      long commentId = fixtures.comment(postId, author, "コメント本文");

      CommentRow row = commentMapper.findRowById(commentId).orElseThrow();

      assertThat(row.id()).isEqualTo(commentId);
      assertThat(row.body()).isEqualTo("コメント本文");
      assertThat(row.createdAt()).isNotNull();
      assertThat(row.editedAt()).isNull();
      assertThat(row.authorId()).isEqualTo(author);
      assertThat(row.authorUsername()).isEqualTo("alice");
      assertThat(row.authorDisplayName()).isEqualTo("aliceの表示名");
      assertThat(row.authorAvatarFileId()).isEqualTo(avatarFileId);
    }
  }

  @Nested
  @DisplayName("論理削除の除外")
  class SoftDelete {

    /** #236 論理削除したコメントは一覧に出ない。 */
    @Test
    @DisplayName("#236 論理削除したコメントは一覧に出ない")
    void 削除済みコメントは出ない() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      long alive = fixtures.comment(postId, author, "生きている");
      long deleted = fixtures.comment(postId, author, "消した");
      commentMapper.softDelete(deleted);

      List<CommentRow> rows = commentMapper.findByPostId(postId, null, null, NO_LIMIT);

      assertThat(rows).extracting(CommentRow::id).containsExactly(alive);
    }

    /** #237 論理削除したコメントは詳細取得もできない（2つのSQL両方）。 */
    @Test
    @DisplayName("#237 論理削除したコメントは findRowById / findById で取得できない")
    void 削除済みコメントは取得できない() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      long deleted = fixtures.comment(postId, author, "消した");
      commentMapper.softDelete(deleted);

      assertThat(commentMapper.findRowById(deleted)).isEmpty();
      assertThat(commentMapper.findById(deleted)).isEmpty();
    }

    /** #238 退会したユーザーのコメントは一覧に出ない（users とのJOIN）。 */
    @Test
    @DisplayName("#238 退会したユーザーのコメントは一覧に出ない")
    void 退会者のコメントは出ない() {
      long author = fixtures.user("alice");
      long gone = fixtures.user("bob");
      long postId = fixtures.post(author, "投稿");
      long mine = fixtures.comment(postId, author, "自分のコメント");
      fixtures.comment(postId, gone, "退会者のコメント");
      fixtures.softDeleteUser(gone);

      List<CommentRow> rows = commentMapper.findByPostId(postId, null, null, NO_LIMIT);

      assertThat(rows).extracting(CommentRow::id).containsExactly(mine);
    }

    /**
     * #239 <b>現状の挙動を固定している（Issue #44 ③）。</b>
     *
     * <p>{@code findByPostId} はコメント自身と投稿者の論理削除は見るが、<b>親投稿が削除済みかは見ない</b>。 現状は {@code
     * CommentService.getComments} が先に投稿の存在を確認して404を返すため実害は無いが、 その順序が変わると削除済み投稿のコメントが漏れる。
     *
     * <p><b>修正時はこのテストの期待値を反転させること。</b>
     */
    @Test
    @DisplayName("#239 親投稿が削除済みでもコメントは返る（Issue #44）")
    void 親投稿が削除済みでもコメントは返る() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      long commentId = fixtures.comment(postId, author, "コメント");
      fixtures.softDeletePost(postId);

      List<CommentRow> rows = commentMapper.findByPostId(postId, null, null, NO_LIMIT);

      // 現状: 親が消えていてもコメントは返る
      assertThat(rows).extracting(CommentRow::id).containsExactly(commentId);
    }
  }

  @Nested
  @DisplayName("更新系SQL")
  class Updates {

    /** #240 二重削除は0件（{@code comment_count} の二重減算を防ぐ最後の砦）。 */
    @Test
    @DisplayName("#240 削除済みのコメントをもう一度softDeleteすると0件")
    void 二重削除は0件() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      long commentId = fixtures.comment(postId, author, "コメント");

      assertThat(commentMapper.softDelete(commentId)).isEqualTo(1);
      assertThat(commentMapper.softDelete(commentId)).isZero();
    }

    /** #241 {@code updateBody} は {@code edited_at} を立て、{@code softDelete} は立てない。 */
    @Test
    @DisplayName("#241 updateBodyは editedAt を立て、softDeleteは立てない")
    void 編集済みフラグの扱い() {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      long edited = fixtures.comment(postId, author, "編集する");
      long removed = fixtures.comment(postId, author, "削除する");

      commentMapper.updateBody(edited, "編集後");
      commentMapper.softDelete(removed);

      assertThat(commentMapper.findRowById(edited).orElseThrow().editedAt()).isNotNull();
      var removedEditedAt =
          jdbc.sql("SELECT edited_at FROM comments WHERE id = ?")
              .param(removed)
              .query(OffsetDateTime.class)
              .optional();
      assertThat(removedEditedAt).isEmpty();
    }
  }
}
