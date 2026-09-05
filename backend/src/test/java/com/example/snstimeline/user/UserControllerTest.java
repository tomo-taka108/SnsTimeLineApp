package com.example.snstimeline.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestAuth;
import com.example.snstimeline.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** ユーザーAPIの結合テスト（docs/11_test_design.md 20章、ケース #313〜#319）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  @Nested
  @DisplayName("フォロー")
  class Follow {

    /** #313 必須項目「自己フォローの拒否」。DBの {@code ck_follows_not_self} に到達する前に400で弾く。 */
    @Test
    @DisplayName("#313 自分をフォローすると400 SELF_FOLLOW_NOT_ALLOWED")
    void 自己フォローは400() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(put("/api/v1/users/{id}/follow", me).with(TestAuth.as(me)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("SELF_FOLLOW_NOT_ALLOWED"));
    }

    /**
     * #314 必須項目「フォローの冪等性」を実DBで確認する。
     *
     * <p>2回フォローしてもフォロワー数は2にならない。{@code uq_follows_follower_followee} と 事前SELECT（D-37）の両方が絡む。
     */
    @Test
    @DisplayName("#314 同じユーザーを2回フォローしてもフォロワー数は1のまま")
    void フォローは冪等() throws Exception {
      long me = fixtures.user("alice");
      long target = fixtures.user("bob");

      mvc.perform(put("/api/v1/users/{id}/follow", target).with(TestAuth.as(me)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.followerCount").value(1));
      mvc.perform(put("/api/v1/users/{id}/follow", target).with(TestAuth.as(me)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.followerCount").value(1));
    }

    /** #315 必須項目「フォロー解除の冪等性」。フォローしていない状態で解除しても壊れない。 */
    @Test
    @DisplayName("#315 フォローしていない状態で解除しても200")
    void フォロー解除は冪等() throws Exception {
      long me = fixtures.user("alice");
      long target = fixtures.user("bob");

      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      "/api/v1/users/{id}/follow", target)
                  .with(TestAuth.as(me)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.followerCount").value(0))
          .andExpect(jsonPath("$.isFollowing").value(false));
    }
  }

  @Nested
  @DisplayName("プロフィール")
  class Profile {

    /** #316 必須項目「プロフィールのカウント算出」。3つのカウントが実データと一致する（D-36）。 */
    @Test
    @DisplayName("#316 postCount / followingCount / followerCount が実データと一致する")
    void カウントが実データと一致する() throws Exception {
      long me = fixtures.user("alice");
      long target = fixtures.user("bob");
      long other = fixtures.user("carol");
      // bob: 投稿2件、フォロー中1人（carol）、フォロワー1人（alice）
      fixtures.post(target, "投稿1");
      fixtures.post(target, "投稿2");
      fixtures.follow(target, other);
      fixtures.follow(me, target);

      mvc.perform(get("/api/v1/users/{id}", target).with(TestAuth.as(me)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.postCount").value(2))
          .andExpect(jsonPath("$.followingCount").value(1))
          .andExpect(jsonPath("$.followerCount").value(1))
          .andExpect(jsonPath("$.isFollowing").value(true))
          .andExpect(jsonPath("$.isMe").value(false));
    }

    /**
     * #317 <b>「未送信」と「明示的なnull」の区別がHTTPの端から端まで効くこと。</b>
     *
     * <p>生の {@code JsonNode} でリクエストを受ける設計（{@code UpdateProfileRequest}）の意義そのもの。 Mapper側の #257 /
     * #258 と対になる。
     */
    @Test
    @DisplayName("#317 bio に null を送ると削除、送らなければ維持される")
    void 未送信と明示的nullの区別() throws Exception {
      long me = fixtures.user("alice");

      // まず bio を設定する
      mvc.perform(
              patch("/api/v1/users/me")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"bio\":\"自己紹介\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.bio").value("自己紹介"));

      // bio を送らない → 維持される
      mvc.perform(
              patch("/api/v1/users/me")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"displayName\":\"新しい名前\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.bio").value("自己紹介"));

      // bio に明示的な null → 削除される
      mvc.perform(
              patch("/api/v1/users/me")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"bio\":null}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.bio").doesNotExist());
    }

    /** #318 {@code avatarFileId} が数値でなければ400（生JsonNodeを手で検証しているため500になりやすい箇所）。 */
    @Test
    @DisplayName("#318 avatarFileId が数値でなければ400")
    void 数値でないavatarFileIdは400() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(
              patch("/api/v1/users/me")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"avatarFileId\":\"abc\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Nested
  @DisplayName("ユーザー検索")
  class Search {

    /** #319 検索結果のページング情報が返り、自分自身は含まれない。 */
    @Test
    @DisplayName("#319 検索結果はOffsetPageの形で返り、自分は含まれない")
    void 検索結果の形() throws Exception {
      long me = fixtures.user("searchme");
      long other = fixtures.user("searchother");

      mvc.perform(get("/api/v1/users").param("q", "search").with(TestAuth.as(me)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(other))
          .andExpect(jsonPath("$.page").value(0))
          .andExpect(jsonPath("$.size").value(20))
          .andExpect(jsonPath("$.totalElements").value(1));
    }
  }
}
