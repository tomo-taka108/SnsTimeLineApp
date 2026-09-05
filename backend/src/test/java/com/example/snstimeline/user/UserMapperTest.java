package com.example.snstimeline.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** {@link UserMapper} の結合テスト（docs/11_test_design.md 19章、ケース #257〜#264）。 */
@SpringBootTest
@Transactional
class UserMapperTest extends AbstractIntegrationTest {

  @Autowired private UserMapper userMapper;
  @Autowired private TestFixtures fixtures;
  @Autowired private JdbcClient jdbc;

  private String bioOf(long userId) {
    return jdbc.sql("SELECT bio FROM users WHERE id = ?")
        .param(userId)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  @Nested
  @DisplayName("プロフィール更新 — 未送信と明示的nullの区別")
  class UpdateProfile {

    /**
     * #257 <b>本章で最も価値の高いテスト。</b>
     *
     * <p>{@code <if test="bioProvided">} を {@code <if test="bio != null">} に書き換えると、
     * 「bioに明示的にnullを送って削除する」ができなくなる。しかし {@code UserServiceTest} は
     * Mapperをモックしているため<b>渡す引数は同じで、テストは緑のまま通る</b>。
     *
     * <p>この違いを検出できるのは<b>実SQLを流すこのテストだけ</b>。生の {@code JsonNode} で リクエストを受けるという設計（{@code
     * UpdateProfileRequest}）が存在する理由そのものを守っている。
     */
    @Test
    @DisplayName("#257 bioProvided=true・bio=null なら bio が消える（明示的な削除）")
    void 明示的なnullでbioが消える() {
      long me = fixtures.user("alice");
      jdbc.sql("UPDATE users SET bio = ? WHERE id = ?").params("元の自己紹介", me).update();

      int affected = userMapper.updateProfile(me, null, true, null, false, null, false, null);

      assertThat(affected).isEqualTo(1);
      assertThat(bioOf(me)).isNull();
    }

    /** #258 #257 と対。未送信（bioProvided=false）なら既存の値が残る。 */
    @Test
    @DisplayName("#258 bioProvided=false なら bio は変更されない（未送信）")
    void 未送信ならbioは変わらない() {
      long me = fixtures.user("alice");
      jdbc.sql("UPDATE users SET bio = ? WHERE id = ?").params("元の自己紹介", me).update();

      int affected = userMapper.updateProfile(me, null, false, null, false, null, false, null);

      assertThat(affected).isEqualTo(1);
      assertThat(bioOf(me)).isEqualTo("元の自己紹介");
    }

    /** #259 displayName は null なら SET 句自体を出さない（COALESCEを使わない理由）。 */
    @Test
    @DisplayName("#259 displayName=null なら表示名は変更されない")
    void displayNameがnullなら変わらない() {
      long me = fixtures.user("alice");

      userMapper.updateProfile(me, null, false, null, false, null, false, null);

      var name =
          jdbc.sql("SELECT display_name FROM users WHERE id = ?")
              .param(me)
              .query(String.class)
              .single();
      assertThat(name).isEqualTo("aliceの表示名");
    }

    /**
     * #260 全フラグがfalseでも {@code <set>} は {@code updated_at = now()} を出すため、 SQL構文エラーにならない（末尾カンマは
     * {@code <set>} が除去する）。
     */
    @Test
    @DisplayName("#260 全フラグfalseでも構文エラーにならず1件更新される")
    void 全て未送信でも更新は成立する() {
      long me = fixtures.user("alice");

      assertThat(userMapper.updateProfile(me, null, false, null, false, null, false, null))
          .isEqualTo(1);
    }

    /** #261 論理削除済みユーザーは更新できない（{@code activeOnly}）。 */
    @Test
    @DisplayName("#261 退会したユーザーのプロフィールは更新できない")
    void 退会者は更新できない() {
      long me = fixtures.user("alice");
      fixtures.softDeleteUser(me);

      assertThat(userMapper.updateProfile(me, "新しい名前", false, null, false, null, false, null))
          .isZero();
    }
  }

  @Nested
  @DisplayName("論理削除の扱い")
  class SoftDelete {

    /** #262 プロフィールの投稿数は論理削除した投稿を数えない（D-36で都度COUNTする方針）。 */
    @Test
    @DisplayName("#262 countPostsは論理削除した投稿を数えない")
    void 投稿数は削除済みを除く() {
      long me = fixtures.user("alice");
      fixtures.post(me, "生きている");
      long deleted = fixtures.post(me, "消した");
      fixtures.softDeletePost(deleted);

      assertThat(userMapper.countPosts(me)).isEqualTo(1);
    }

    /**
     * #263 退会したユーザーはログイン・取得の対象にならない。
     *
     * <p><b>メールアドレスは Mapper ではなく JdbcClient で取得する。</b> MyBatis の1次キャッシュ （{@code localCacheScope} 既定
     * {@code SESSION}）により、論理削除の<b>前</b>に {@code findById} を
     * 呼ぶと、削除後の同じ問い合わせがキャッシュを返してしまい、SQLではなくキャッシュを検証することになる。
     */
    @Test
    @DisplayName("#263 退会したユーザーは findByEmail / findById で取得できない")
    void 退会者は取得できない() {
      long me = fixtures.user("alice");
      String email =
          jdbc.sql("SELECT email FROM users WHERE id = ?").param(me).query(String.class).single();
      fixtures.softDeleteUser(me);

      assertThat(userMapper.findById(me)).isEmpty();
      assertThat(userMapper.findByEmail(email)).isEmpty();
    }

    /**
     * #264 <b>{@code *IncludingDeleted} は意図的に論理削除を無視する。</b>
     *
     * <p>一意制約が {@code deleted_at} を絡めない設計＝退会したメール・ユーザー名は再利用させない方針 （04_data_model.md 2.1）。ここに {@code
     * activeOnly} を足すと、退会者のメールで再登録できてしまい、 UNIQUE制約違反で409に落ちる。
     */
    @Test
    @DisplayName("#264 existsBy*IncludingDeleted は退会者も含めて true を返す")
    void 退会者のメールとユーザー名は再利用できない() {
      long me = fixtures.user("alice");
      String email =
          jdbc.sql("SELECT email FROM users WHERE id = ?").param(me).query(String.class).single();
      fixtures.softDeleteUser(me);

      assertThat(userMapper.existsByEmailIncludingDeleted(email)).isTrue();
      assertThat(userMapper.existsByUsernameIncludingDeleted("alice")).isTrue();
    }
  }
}
