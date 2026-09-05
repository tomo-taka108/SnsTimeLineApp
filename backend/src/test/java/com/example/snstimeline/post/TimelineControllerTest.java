package com.example.snstimeline.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestAuth;
import com.example.snstimeline.support.TestFixtures;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** タイムラインAPIの結合テスト（docs/11_test_design.md 20章、ケース #303〜#308）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TimelineControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  /**
   * #303 必須項目「論理削除の除外」を<b>HTTPの端から端まで</b>確認する。
   *
   * <p>Mapper層の #204 が実際にレスポンスへ反映されることの確認。
   */
  @Test
  @DisplayName("#303 論理削除した投稿はタイムラインのレスポンスに出ない")
  void 削除済み投稿はレスポンスに出ない() throws Exception {
    long me = fixtures.user("alice");
    long alive = fixtures.post(me, "生きている投稿");
    long deleted = fixtures.post(me, "消した投稿");
    fixtures.softDeletePost(deleted);

    mvc.perform(get("/api/v1/timeline").with(TestAuth.as(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(alive))
        .andExpect(jsonPath("$.items[*].id").value(Matchers.not(Matchers.hasItem((int) deleted))));
  }

  /**
   * #304 <b>空のタイムラインが500にならないこと。</b>
   *
   * <p>投稿が0件のとき、いいね判定の一括取得に空リストが渡ると {@code IN ()} で構文エラーになる。 {@code PostService.likedPostIdsOf}
   * のガード（Mapper側 #256 が契約を固定）が 実際に効いていることを、ここで端から端まで確認する。
   */
  @Test
  @DisplayName("#304 投稿が0件でも200で空配列が返る（IN () ガードが効いている）")
  void 空のタイムラインは200() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/timeline").with(TestAuth.as(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  /**
   * #305 <b>カーソルがHTTPのクエリ文字列を往復できること。</b>
   *
   * <p>URLセーフBase64を使う理由そのもの（標準Base64の {@code +} はクエリ文字列で空白に化ける）。 実際にHTTP経由で往復させて、取りこぼしが無いことを確認する。
   */
  @Test
  @DisplayName("#305 カーソルをHTTPで往復させても取りこぼさない")
  void カーソルのHTTP往復() throws Exception {
    long me = fixtures.user("alice");
    fixtures.post(me, "1件目");
    fixtures.post(me, "2件目");
    fixtures.post(me, "3件目");

    // Jackson の版差（本プロジェクトは Jackson 3）に依存しないよう、JsonPath で取り出す
    String body =
        mvc.perform(get("/api/v1/timeline").param("limit", "2").with(TestAuth.as(me)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

    String cursor = com.jayway.jsonpath.JsonPath.read(body, "$.nextCursor");

    mvc.perform(
            get("/api/v1/timeline")
                .param("limit", "2")
                .param("cursor", cursor)
                .with(TestAuth.as(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
  }

  /** #306 壊れたカーソルは400（{@code CursorCodec} の例外が500ではなく400として届くこと）。 */
  @Test
  @DisplayName("#306 壊れたカーソルは400")
  void 壊れたカーソルは400() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/timeline").param("cursor", "!!!").with(TestAuth.as(me)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  /** #307 取得件数の上限は50（06_non_functional.md 1.3）。 */
  @Test
  @DisplayName("#307 limit=51は400、limit=50は200")
  void 取得件数の上限() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/timeline").param("limit", "51").with(TestAuth.as(me)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(get("/api/v1/timeline").param("limit", "50").with(TestAuth.as(me)))
        .andExpect(status().isOk());
  }

  /** #308 新着件数の {@code sinceId} は必須パラメータ（無いと400）。 */
  @Test
  @DisplayName("#308 new-count は sinceId が無いと400")
  void 新着件数の必須パラメータ() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/timeline/new-count").with(TestAuth.as(me)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
