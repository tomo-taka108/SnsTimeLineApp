package com.example.snstimeline.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * エラーレスポンスのJSON契約（docs/11_test_design.md 20章、ケース #285〜#291）。
 *
 * <p>クライアントは {@code code} で分岐し、{@code message} をそのまま画面に出す。 形が変わると全画面のエラー表示が壊れるため、形状そのものをテストで固定する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErrorResponseShapeTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  /**
   * #285 <b>{@code timestamp} は秒精度でミリ秒を持たない。</b>
   *
   * <p>{@code Instant.now().truncatedTo(SECONDS)} の丸めが外れると {@code .123} が付き、 設計書に書いた例（{@code
   * 2026-08-17T12:34:56Z}）と食い違う。 {@code isNotNull()} では検出できないため正規表現で固定する。
   */
  @Test
  @DisplayName("#285 timestampは秒精度のISO8601（ミリ秒なし・末尾Z）")
  void タイムスタンプの形式() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/posts/{id}", 999999).with(TestAuth.as(me)))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.timestamp")
                .value(Matchers.matchesPattern("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")));
  }

  /** #286 {@code path} はリクエストURIそのもの（{@code /api/v1} プレフィックスとパス変数を含む）。 */
  @Test
  @DisplayName("#286 pathは実際のリクエストURIと一致する")
  void パスが正しい() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/posts/{id}", 999999).with(TestAuth.as(me)))
        .andExpect(jsonPath("$.path").value("/api/v1/posts/999999"));
  }

  /** #287 {@code status} はHTTPステータスと一致し、{@code code} は {@code ErrorCode} の enum 名。 */
  @Test
  @DisplayName("#287 statusはHTTPステータスと一致し、codeはenum名")
  void ステータスとコード() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/posts/{id}", 999999).with(TestAuth.as(me)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("リソースが見つかりません"));
  }

  /**
   * #288 <b>バリデーションエラーのときだけ {@code errors} が入る。</b>
   *
   * <p>#276（未認証401）で「存在しない」ことを確認済み。両側から {@code @JsonInclude(NON_NULL)} の挙動を固定する。
   */
  @Test
  @DisplayName("#288 バリデーションエラーでは errors 配列が入る")
  void バリデーションエラーの形() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(
            post("/api/v1/posts")
                .with(TestAuth.as(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors[0].field").value("body"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
  }

  /**
   * #289 必須クエリパラメータの欠落は400。
   *
   * <p>{@code MissingServletRequestParameterException} のハンドラが無いと<b>500になる</b> （{@code
   * GlobalExceptionHandler} のJavadocに明記されている）。
   */
  @Test
  @DisplayName("#289 必須パラメータ q が無いと400")
  void 必須パラメータの欠落() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/users").with(TestAuth.as(me)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("必要なパラメータが指定されていません"));
  }

  /** #290 パス変数の型が違えば400（ハンドラが無いと500）。 */
  @Test
  @DisplayName("#290 パス変数が数値でないと400")
  void パス変数の型不一致() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(get("/api/v1/posts/{id}", "abc").with(TestAuth.as(me)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("パラメータの形式が正しくありません"));
  }

  /** #291 壊れたJSONは400（ハンドラが無いと500）。 */
  @Test
  @DisplayName("#291 壊れたJSONは400")
  void 壊れたJSON() throws Exception {
    long me = fixtures.user("alice");

    mvc.perform(
            post("/api/v1/posts")
                .with(TestAuth.as(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("リクエストの形式が正しくありません"));
  }
}
