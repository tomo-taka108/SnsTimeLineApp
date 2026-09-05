package com.example.snstimeline.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link UserMapper} のユーザー検索SQLの結合テスト（docs/11_test_design.md 19章、ケース #265〜#270）。
 *
 * <p>{@code UserSearchServiceTest}（13章）は Java 側のエスケープ処理を検証したが、 <b>SQL側に {@code ESCAPE '\'}
 * が実際に書かれているか</b>は実DBでしか確認できない。 これが無いと {@code q=%} で全ユーザーが列挙される（04_data_model.md 6.5 要求3）。
 *
 * <p>{@code searchUsers} と {@code countSearchUsers} は {@code <sql id="searchWhere">} を共有する。
 * 片方だけ書き換えると総ページ数と実際の件数が食い違うため、両方を突き合わせる。
 */
@SpringBootTest
@Transactional
class UserMapperSearchTest extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private TestFixtures fixtures;

  /** 検索は「エスケープ済み」と「生」の2種類のキーワードを取る。通常はどちらも同じ値。 */
  private List<UserSearchRow> search(String q, long meId, int size, int offset) {
    return userMapper.searchUsers(q, q, meId, size, offset);
  }

  @Nested
  @DisplayName("LIKEのエスケープ")
  class LikeEscape {

    /**
     * #265 <b>{@code ESCAPE '\'} がSQLに書かれていることの確認。</b>
     *
     * <p>Service層がエスケープした {@code \%} を渡したとき、SQL側が {@code ESCAPE '\'} を持っていれば
     * 「リテラルの%」として扱われ、%を含まないユーザーにはヒットしない。 {@code ESCAPE '\'} が無いと {@code \%} の {@code %}
     * がワイルドカードとして働き、<b>全ユーザーが返る</b>。
     *
     * <p>これはSQLインジェクションではない（バインドは効いている）が、情報漏洩としては同等に危険。
     */
    @Test
    @DisplayName("#265 エスケープ済みの\\%は全ユーザーを列挙しない")
    void パーセントは全件列挙にならない() {
      long me = fixtures.user("alice");
      fixtures.user("bob");
      fixtures.user("carol");

      // UserSearchService.escapeLikePattern("%") の出力そのもの
      List<UserSearchRow> rows = search("\\%", me, 50, 0);

      assertThat(rows).isEmpty();
    }

    /** #266 アンダースコアも同様。エスケープ済みなら「任意の1文字」ではなくリテラルとして扱われる。 */
    @Test
    @DisplayName("#266 エスケープ済みの\\_は任意1文字にマッチしない")
    void アンダースコアはリテラル扱い() {
      long me = fixtures.user("alice");
      fixtures.user("bob");

      List<UserSearchRow> rows = search("\\_", me, 50, 0);

      assertThat(rows).isEmpty();
    }

    /** #267 通常のキーワードは部分一致する（エスケープが検索そのものを壊していないことの確認）。 */
    @Test
    @DisplayName("#267 通常のキーワードは部分一致する")
    void 通常のキーワードは一致する() {
      long me = fixtures.user("alice");
      long bob = fixtures.user("bob");

      List<UserSearchRow> rows = search("bo", me, 50, 0);

      assertThat(rows).extracting(UserSearchRow::userId).containsExactly(bob);
    }
  }

  @Nested
  @DisplayName("searchUsers と countSearchUsers の一致")
  class ListAndCount {

    /** #268 共有フラグメント {@code searchWhere} により、一覧と件数の条件が常に一致すること。 */
    @Test
    @DisplayName("#268 論理削除したユーザーは一覧にも件数にも出ない")
    void 退会者は一覧にも件数にも出ない() {
      long me = fixtures.user("alice");
      long alive = fixtures.user("targetalive");
      long gone = fixtures.user("targetgone");
      fixtures.softDeleteUser(gone);

      List<UserSearchRow> rows = search("target", me, 50, 0);
      long count = userMapper.countSearchUsers("target", "target", me);

      assertThat(rows).extracting(UserSearchRow::userId).containsExactly(alive);
      assertThat(count).isEqualTo(1);
    }

    /** #269 自分自身は検索結果から除外される（04_data_model.md 6.5 要求6）。一覧・件数の両方で。 */
    @Test
    @DisplayName("#269 自分自身は一覧にも件数にも出ない")
    void 自分は検索結果に出ない() {
      long me = fixtures.user("targetme");
      long other = fixtures.user("targetother");

      List<UserSearchRow> rows = search("target", me, 50, 0);
      long count = userMapper.countSearchUsers("target", "target", me);

      assertThat(rows).extracting(UserSearchRow::userId).containsExactly(other).doesNotContain(me);
      assertThat(count).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("行マッピングとページング")
  class MappingAndPaging {

    /** #270 {@code UserSearchRow} の全5フィールドがマッピングされること。認証情報を含まないことも型で保証される。 */
    @Test
    @DisplayName("#270 UserSearchRowの全フィールドが正しくマッピングされる")
    void 全フィールドがマッピングされる() {
      long me = fixtures.user("alice");
      long target = fixtures.user("targetuser");
      long avatarFileId = fixtures.storedFile(target);
      userMapper.updateProfile(target, null, true, "自己紹介", true, avatarFileId, false, null);

      List<UserSearchRow> rows = search("targetuser", me, 50, 0);

      assertThat(rows).hasSize(1);
      UserSearchRow row = rows.get(0);
      assertThat(row.userId()).isEqualTo(target);
      assertThat(row.username()).isEqualTo("targetuser");
      assertThat(row.displayName()).isEqualTo("targetuserの表示名");
      assertThat(row.bio()).isEqualTo("自己紹介");
      assertThat(row.avatarFileId()).isEqualTo(avatarFileId);
    }

    /**
     * #271 オフセットページングのタイブレーカー。
     *
     * <p>{@code ORDER BY} の末尾にある {@code id}（「省略不可」とコメントされている）が無いと、
     * 関連度が同じユーザー同士の並びが不定になり、ページをまたいで重複・欠落が起きる。 本アプリで唯一オフセット方式を使う箇所（05_api_design.md 2.2）。
     */
    @Test
    @DisplayName("#271 同じ関連度のユーザーがページをまたいでも重複・欠落しない")
    void オフセットのタイブレーカー() {
      long me = fixtures.user("alice");
      // 同じ前方一致・同じ長さ＝関連度が同じになる3人
      long u1 = fixtures.user("samexxx1");
      long u2 = fixtures.user("samexxx2");
      long u3 = fixtures.user("samexxx3");

      List<Long> page1 = search("samexxx", me, 2, 0).stream().map(UserSearchRow::userId).toList();
      List<Long> page2 = search("samexxx", me, 2, 2).stream().map(UserSearchRow::userId).toList();

      assertThat(page1).hasSize(2);
      assertThat(page2).hasSize(1);
      List<Long> all = new java.util.ArrayList<>(page1);
      all.addAll(page2);
      assertThat(all).containsExactlyInAnyOrder(u1, u2, u3).doesNotHaveDuplicates();
    }
  }
}
