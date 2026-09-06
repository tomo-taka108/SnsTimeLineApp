package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** 投稿APIの結合テスト（docs/11_test_design.md 20章、ケース #292〜#302）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  @Nested
  @DisplayName("論理削除された投稿の扱い")
  class SoftDeleted {

    /** #292 必須項目「論理削除の除外」のうち<b>「GETで404になる」</b>側。Mapper側の #208 と対。 */
    @Test
    @DisplayName("#292 論理削除した投稿のGETは404")
    void 削除済み投稿のGETは404() throws Exception {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");
      fixtures.softDeletePost(postId);

      mvc.perform(get("/api/v1/posts/{id}", postId).with(TestAuth.as(me)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /** #293 削除済み投稿の編集も404（所有者本人でも）。存在チェックが所有者チェックより先（D-14）。 */
    @Test
    @DisplayName("#293 論理削除した投稿のPATCHは404（所有者本人でも）")
    void 削除済み投稿のPATCHは404() throws Exception {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");
      fixtures.softDeletePost(postId);

      mvc.perform(
              patch("/api/v1/posts/{id}", postId)
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"編集後\"}"))
          .andExpect(status().isNotFound());
    }

    /** #294 二重削除は404（Mapper側 #225 の affected==0 がここに繋がる）。 */
    @Test
    @DisplayName("#294 同じ投稿を2回削除すると2回目は404")
    void 二重削除は404() throws Exception {
      long me = fixtures.user("alice");
      long postId = fixtures.post(me, "投稿");

      mvc.perform(delete("/api/v1/posts/{id}", postId).with(TestAuth.as(me)))
          .andExpect(status().isNoContent());
      mvc.perform(delete("/api/v1/posts/{id}", postId).with(TestAuth.as(me)))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("ファイル所有者チェック")
  class FileOwnership {

    /** #295 必須項目「ファイル所有者チェック」。他人のfileIdを添付した投稿は403。 */
    @Test
    @DisplayName("#295 他人のfileIdを指定した投稿は403")
    void 他人のファイルは403() throws Exception {
      long me = fixtures.user("alice");
      long other = fixtures.user("bob");
      long fileId = fixtures.storedFile(other);

      mvc.perform(
              post("/api/v1/posts")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"本文\",\"imageFileIds\":[" + fileId + "]}"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** #296 存在しないfileIdは403ではなく404（D-14の順序。#295と対にして初めて意味を持つ）。 */
    @Test
    @DisplayName("#296 存在しないfileIdを指定した投稿は404（403ではない）")
    void 存在しないファイルは404() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(
              post("/api/v1/posts")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"本文\",\"imageFileIds\":[999999]}"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    /** #297 自分のfileIdなら投稿できる（#295と対）。 */
    @Test
    @DisplayName("#297 自分のfileIdなら投稿できる")
    void 自分のファイルは投稿できる() throws Exception {
      long me = fixtures.user("alice");
      long fileId = fixtures.storedFile(me);

      mvc.perform(
              post("/api/v1/posts")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"本文\",\"imageFileIds\":[" + fileId + "]}"))
          .andExpect(status().isCreated());
    }
  }

  @Nested
  @DisplayName("いいねの冪等性")
  class LikeIdempotency {

    /**
     * #298 必須項目「いいねの冪等性」を<b>実DBで</b>確認する。
     *
     * <p>{@code LikeServiceTest} #55 はMapperをモックしていたため、UNIQUE制約と
     * 非正規化カウンタの実際の挙動は見ていない。ここが初めての実地検証になる。
     */
    @Test
    @DisplayName("#298 同じ投稿に2回いいねしてもカウンタは1のまま")
    void いいねは冪等() throws Exception {
      long author = fixtures.user("alice");
      long liker = fixtures.user("bob");
      long postId = fixtures.post(author, "投稿");

      mvc.perform(put("/api/v1/posts/{id}/like", postId).with(TestAuth.as(liker)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(1));
      mvc.perform(put("/api/v1/posts/{id}/like", postId).with(TestAuth.as(liker)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(1));

      assertThat(fixtures.likeCountOf(postId)).isEqualTo(1);
    }

    /**
     * #299 必須項目「いいね解除の冪等性」。
     *
     * <p>ガードが外れると {@code ck_posts_like_count (>= 0)} に引っかかって500になる。
     */
    @Test
    @DisplayName("#299 いいねしていない状態で解除しても壊れない")
    void いいね解除は冪等() throws Exception {
      long author = fixtures.user("alice");
      long liker = fixtures.user("bob");
      long postId = fixtures.post(author, "投稿");

      mvc.perform(delete("/api/v1/posts/{id}/like", postId).with(TestAuth.as(liker)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.likeCount").value(0));

      assertThat(fixtures.likeCountOf(postId)).isZero();
    }
  }

  @Nested
  @DisplayName("投稿削除時のカウンタ（非対称ルール）")
  class CounterAsymmetry {

    /**
     * #300 必須項目「投稿削除時のカウンタ」。
     *
     * <p>投稿を論理削除しても {@code comment_count} は変わらない（設計判断②の非対称ルール）。
     * コメントはカスケード削除されないため、カウンタを減らすとむしろ整合しない。
     */
    @Test
    @DisplayName("#300 投稿を削除しても comment_count は変わらない")
    void 投稿削除でコメント数は変わらない() throws Exception {
      long author = fixtures.user("alice");
      long postId = fixtures.post(author, "投稿");
      fixtures.comment(postId, author, "コメント");
      assertThat(fixtures.commentCountOf(postId)).isEqualTo(1);

      mvc.perform(delete("/api/v1/posts/{id}", postId).with(TestAuth.as(author)))
          .andExpect(status().isNoContent());

      assertThat(fixtures.commentCountOf(postId)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("正常系のレスポンス")
  class HappyPath {

    /** #301 作成は201で、本文と作者と初期カウンタが返る。日本語がそのまま往復すること。 */
    @Test
    @DisplayName("#301 投稿の作成は201で日本語がそのまま返る")
    void 投稿の作成() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(
              post("/api/v1/posts")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"日本語の本文です\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.body").value("日本語の本文です"))
          .andExpect(jsonPath("$.author.username").value("alice"))
          .andExpect(jsonPath("$.likeCount").value(0))
          .andExpect(jsonPath("$.commentCount").value(0));
    }

    /** #302 本文が280コードポイントを超えると400（絵文字はサロゲートペアだが1文字と数える）。 */
    @Test
    @DisplayName("#302 本文が281文字なら400、280文字なら201")
    void 本文の文字数境界() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(
              post("/api/v1/posts")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"" + "あ".repeat(281) + "\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

      mvc.perform(
              post("/api/v1/posts")
                  .with(TestAuth.as(me))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"" + "あ".repeat(280) + "\"}"))
          .andExpect(status().isCreated());
    }
  }
}
