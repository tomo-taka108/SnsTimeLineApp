package com.example.snstimeline.follow;

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
import org.springframework.transaction.annotation.Transactional;

/** {@link FollowMapper} の結合テスト（docs/11_test_design.md 19章、ケース #242〜#247）。 */
@SpringBootTest
@Transactional
class FollowMapperTest extends AbstractIntegrationTest {

  @Autowired private FollowMapper followMapper;
  @Autowired private TestFixtures fixtures;
  @Autowired private org.springframework.jdbc.core.simple.JdbcClient jdbc;

  private static final int NO_LIMIT = 100;

  @Nested
  @DisplayName("行マッピングの完全性")
  class RowMapping {

    /**
     * #242 <b>{@code followCreatedAt} と {@code userCreatedAt} の取り違えを検出する。</b>
     *
     * <p>{@code f.created_at AS follow_created_at} と {@code u.created_at AS user_created_at} は
     * 本プロジェクトで最も入れ替わりやすい別名の組。入れ替わると {@code FollowService.toPage} が<b>ユーザー登録日時でカーソルを組んでしまい</b>、
     * フォロー中一覧のページングが不可解に壊れる。
     *
     * <p><b>非nullチェックだけでは検出できない</b>ため、2つが異なる値であることまで確認する。 そのためにユーザー作成とフォローの間に別のDML（投稿作成）を挟み、時刻をずらす。
     */
    @Test
    @DisplayName("#242 FollowRowの全フィールドが正しくマッピングされる（follow日時とuser日時は別物）")
    void 全フィールドがマッピングされる() {
      long me = fixtures.user("alice");
      long target = fixtures.user("bob");
      long avatarFileId = fixtures.storedFile(target);
      // bio / avatar を埋めて全フィールドを非nullにし、users.created_at を過去にずらして
      // follow_created_at と区別できるようにする（@Transactional 内では now() が固定のため）
      jdbc.sql(
              """
              UPDATE users SET bio = ?, avatar_file_id = ?, created_at = now() - interval '1 day'
              WHERE id = ?
              """)
          .params("自己紹介", avatarFileId, target)
          .update();
      fixtures.follow(me, target);

      List<FollowRow> rows = followMapper.findFollowing(me, null, null, NO_LIMIT);

      assertThat(rows).hasSize(1);
      FollowRow row = rows.get(0);
      assertThat(row.followId()).isNotNull();
      assertThat(row.followCreatedAt()).isNotNull();
      assertThat(row.userId()).isEqualTo(target);
      assertThat(row.username()).isEqualTo("bob");
      assertThat(row.displayName()).isEqualTo("bobの表示名");
      assertThat(row.bio()).isEqualTo("自己紹介");
      assertThat(row.avatarFileId()).isEqualTo(avatarFileId);
      assertThat(row.userCreatedAt()).isNotNull();
      // 別名が入れ替わっていればこの2つは同じ値になる
      assertThat(row.followCreatedAt()).isAfter(row.userCreatedAt());
    }
  }

  @Nested
  @DisplayName("フォロー一覧")
  class Listing {

    /** #243 フォロー中一覧は自分がフォローしている相手を返す。 */
    @Test
    @DisplayName("#243 findFollowingはフォローしている相手を返す")
    void フォロー中一覧() {
      long me = fixtures.user("alice");
      long target = fixtures.user("bob");
      long stranger = fixtures.user("carol");
      fixtures.follow(me, target);

      List<FollowRow> rows = followMapper.findFollowing(me, null, null, NO_LIMIT);

      assertThat(rows).extracting(FollowRow::userId).containsExactly(target);
      assertThat(stranger).isPositive();
    }

    /** #244 フォロワー一覧は自分をフォローしている相手を返す（findFollowingとは逆方向）。 */
    @Test
    @DisplayName("#244 findFollowersはフォローしてくる相手を返す")
    void フォロワー一覧() {
      long me = fixtures.user("alice");
      long follower = fixtures.user("bob");
      fixtures.follow(follower, me);

      List<FollowRow> rows = followMapper.findFollowers(me, null, null, NO_LIMIT);

      assertThat(rows).extracting(FollowRow::userId).containsExactly(follower);
    }

    /** #245 退会したユーザーは一覧に出ない（users とのJOIN）。 */
    @Test
    @DisplayName("#245 退会したユーザーはフォロー中一覧に出ない")
    void 退会者は一覧に出ない() {
      long me = fixtures.user("alice");
      long gone = fixtures.user("bob");
      fixtures.follow(me, gone);
      fixtures.softDeleteUser(gone);

      List<FollowRow> rows = followMapper.findFollowing(me, null, null, NO_LIMIT);

      assertThat(rows).isEmpty();
    }
  }

  @Nested
  @DisplayName("論理削除の扱いが list と count で異なる箇所（Issue #44）")
  class SoftDeleteInconsistency {

    /**
     * #246 <b>現状の挙動を固定している。本来は list と count が一致すべき（Issue #44 ②）。</b>
     *
     * <p>{@code countFollowing} は {@code SELECT COUNT(*) FROM follows} だけで users を見ないため、
     * 退会したユーザーも数える。一方 {@code findFollowing} は users とJOINして除外する。 利用者から見ると「フォロー中 1」と表示されるのに、一覧を開くと空。
     *
     * <p><b>修正時はこのテストの期待値を反転させること。</b>
     */
    @Test
    @DisplayName("#246 countFollowingは退会ユーザーを数えるが、findFollowingは返さない（Issue #44）")
    void フォロー中の件数と一覧が食い違う() {
      long me = fixtures.user("alice");
      long gone = fixtures.user("bob");
      fixtures.follow(me, gone);
      fixtures.softDeleteUser(gone);

      int count = followMapper.countFollowing(me);
      List<FollowRow> rows = followMapper.findFollowing(me, null, null, NO_LIMIT);

      assertThat(count).isEqualTo(1); // 現状: 数える
      assertThat(rows).isEmpty(); // 現状: 一覧には出ない
    }

    /** #247 フォロワー側も同じ食い違いがある（Issue #44 ②の対）。 */
    @Test
    @DisplayName("#247 countFollowersも退会ユーザーを数える（Issue #44）")
    void フォロワーの件数と一覧が食い違う() {
      long me = fixtures.user("alice");
      long gone = fixtures.user("bob");
      fixtures.follow(gone, me);
      fixtures.softDeleteUser(gone);

      int count = followMapper.countFollowers(me);
      List<FollowRow> rows = followMapper.findFollowers(me, null, null, NO_LIMIT);

      assertThat(count).isEqualTo(1);
      assertThat(rows).isEmpty();
    }
  }

  @Nested
  @DisplayName("一括取得（N+1回避）")
  class BulkLookup {

    /** #248 フォロー済み判定の一括取得。指定したIDのうちフォロー中のものだけを返す。 */
    @Test
    @DisplayName("#248 findFollowedUserIdsはフォロー中のIDだけを返す")
    void フォロー済みIDの一括取得() {
      long me = fixtures.user("alice");
      long followed = fixtures.user("bob");
      long notFollowed = fixtures.user("carol");
      fixtures.follow(me, followed);

      List<Long> ids = followMapper.findFollowedUserIds(me, List.of(followed, notFollowed));

      assertThat(ids).containsExactly(followed);
    }

    /**
     * #249 <b>空リストを渡すと {@code IN ()} になり構文エラーになる。</b>
     *
     * <p>ガードは呼び出し側（{@code FollowService.toPage} / {@code UserSearchService.search}）にあり、
     * Mapper自身は防いでいない。この契約をテストで固定しておくことで、 ガードを外したときに何が起きるかが明示される（Controller層の #285 と対）。
     */
    @Test
    @DisplayName("#249 空リストを渡すとSQL構文エラーになる（ガードは呼び出し側の責務）")
    void 空リストは構文エラー() {
      long me = fixtures.user("alice");

      assertThatThrownBy(() -> followMapper.findFollowedUserIds(me, List.of()))
          .isInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class);
    }
  }
}
