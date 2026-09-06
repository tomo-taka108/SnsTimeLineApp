package com.example.snstimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestAuth;
import com.example.snstimeline.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** コメントAPIの結合テスト（docs/11_test_design.md 20章、ケース #309〜#312）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  /**
   * #309 必須項目「コメント削除時のカウンタ」を<b>実DBで</b>確認する。
   *
   * <p>コメントを論理削除すると {@code comment_count} が -1 される（設計判断②）。 投稿削除では変わらない（#300）のと対になる非対称ルール。
   */
  @Test
  @DisplayName("#309 コメントの追加と削除で commentCount が増減する")
  void コメント数の増減() throws Exception {
    long author = fixtures.user("alice");
    long postId = fixtures.post(author, "投稿");

    String body =
        mvc.perform(
                post("/api/v1/posts/{postId}/comments", postId)
                    .with(TestAuth.as(author))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"コメント本文\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(fixtures.commentCountOf(postId)).isEqualTo(1);

    int commentId = com.jayway.jsonpath.JsonPath.read(body, "$.comment.id");

    mvc.perform(delete("/api/v1/comments/{id}", commentId).with(TestAuth.as(author)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commentCount").value(0));
    assertThat(fixtures.commentCountOf(postId)).isZero();
  }

  /** #310 他人のコメントは削除できない（403）。 */
  @Test
  @DisplayName("#310 他人のコメントを削除すると403")
  void 他人のコメントは削除できない() throws Exception {
    long author = fixtures.user("alice");
    long other = fixtures.user("bob");
    long postId = fixtures.post(author, "投稿");
    long commentId = fixtures.comment(postId, author, "コメント");

    mvc.perform(delete("/api/v1/comments/{id}", commentId).with(TestAuth.as(other)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  /** #311 存在しない投稿へのコメント一覧取得は404。 */
  @Test
  @DisplayName("#311 存在しない投稿のコメント一覧は404")
  void 存在しない投稿のコメント一覧() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/posts/{postId}/comments", 999999).with(TestAuth.as(me)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  /** #312 コメント一覧は古い順に返る（投稿一覧とは逆向き。Mapper側 #234 のHTTP経由での確認）。 */
  @Test
  @DisplayName("#312 コメント一覧は古い順に返る")
  void コメントは古い順() throws Exception {
    long author = fixtures.user("alice");
    long postId = fixtures.post(author, "投稿");
    long first = fixtures.comment(postId, author, "1番目");
    long second = fixtures.comment(postId, author, "2番目");

    mvc.perform(get("/api/v1/posts/{postId}/comments", postId).with(TestAuth.as(author)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(first))
        .andExpect(jsonPath("$.items[1].id").value(second));
  }
}
