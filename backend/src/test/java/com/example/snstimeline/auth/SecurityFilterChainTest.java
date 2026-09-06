package com.example.snstimeline.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * セキュリティチェーンの結合テスト（docs/11_test_design.md 20章、ケース #276〜#284）。
 *
 * <p><b>Service層の単体テストでは検証できない領域。</b> 401 / 403 はフィルタチェーンの中で起きるため {@code GlobalExceptionHandler}
 * でも捕捉できず（そのJavadocに明記されている）、 実際にHTTPリクエストを流して初めて確認できる。
 *
 * <p>認証済みにするには {@link TestAuth#as(long)} を使う。{@code @WithMockUser} は使えない —— コントローラは
 * {@code @AuthenticationPrincipal AuthPrincipal} を受け取るため、 {@code UserDetails} を積むと null が注入されて
 * 401/403 ではなく<b>500</b>になる。
 *
 * <p>日本語メッセージの検証は {@code jsonPath(...).value(...)} を使う。 {@code
 * MockHttpServletResponse.getContentAsString()} は charset が無いと ISO-8859-1 に落ちて文字化けするため。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityFilterChainTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  @Nested
  @DisplayName("未認証（401）")
  class Unauthenticated {

    /**
     * #276 <b>401のJSON契約そのもの。</b>
     *
     * <p>{@code AuthEntryPoint} が返す形を固定する。{@code .exceptionHandling(...)} の設定が外れると
     * SpringのデフォルトHTMLページが返り、クライアントの分岐が丸ごと壊れる。
     *
     * <p>{@code errors} は検証エラー時のみ入る（{@code @JsonInclude(NON_NULL)}）。 ここで「存在しない」ことを確認し、#283
     * で「存在する」ことを確認して両側から固定する。
     */
    @Test
    @DisplayName("#276 未認証でタイムラインを叩くと401とUNAUTHENTICATEDのJSONが返る")
    void 未認証は401() throws Exception {
      mvc.perform(get("/api/v1/timeline"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
          .andExpect(jsonPath("$.message").value("認証が必要です"))
          .andExpect(jsonPath("$.path").value("/api/v1/timeline"))
          .andExpect(jsonPath("$.errors").doesNotExist());
    }

    /**
     * #277 <b>認証はバリデーションより先に走る。</b>
     *
     * <p>順序が逆になると、未認証の相手にバリデーション規則を教えてしまう。
     */
    @Test
    @DisplayName("#277 未認証なら本文が不正でも400ではなく401")
    void 認証がバリデーションより先() throws Exception {
      mvc.perform(
              post("/api/v1/posts")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"body\":\"\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("#278 /auth/me は認証が必要")
    void auth_meは認証が必要() throws Exception {
      mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    /** #279 logout は refresh と違い認証が要る（誰のトークンを失効させるか知る必要があるため）。 */
    @Test
    @DisplayName("#279 /auth/logout は認証が必要（refreshとは異なる）")
    void logoutは認証が必要() throws Exception {
      mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("permitAll の範囲")
  class PermitAll {

    /** #280 signup / login は未認証で通る。ここが壊れると誰も登録・ログインできなくなる。 */
    @Test
    @DisplayName("#280 signup は未認証で201を返す")
    void signupは未認証で通る() throws Exception {
      mvc.perform(
              post("/api/v1/auth/signup")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"newuser@example.com","username":"newuser",
                       "displayName":"新規ユーザー","password":"Password1"}
                      """))
          .andExpect(status().isCreated());
    }

    /**
     * #281 <b>{@code GET /files/{id}} は認証不要だが、{@code POST /files} は認証が要る。</b>
     *
     * <p>{@code <img src>} は Authorization ヘッダを送れないため配信だけを公開している。 {@code HttpMethod.GET}
     * の修飾が外れるとアップロードまで公開され、実害のあるセキュリティ回帰になる。
     */
    @Test
    @DisplayName("#281 GET /files/{id} は認証不要、POST /files は401")
    void ファイル配信は公開でアップロードは要認証() throws Exception {
      // 存在しないIDでも「401ではない」ことが要点（permitAllを通過して404になる）
      mvc.perform(get("/api/v1/files/999999")).andExpect(status().isNotFound());

      mvc.perform(post("/api/v1/files")).andExpect(status().isUnauthorized());
    }

    /**
     * #281b <b>permitAll は GET だけに限定されていること。</b>
     *
     * <p>マッチャは {@code /api/v1/files/*}（1セグメント）に対する <b>GET のみ</b>。 {@code HttpMethod.GET}
     * の修飾が外れると、同じパスへの書き込み系メソッドまで 認証不要になる。#281 の {@code POST /api/v1/files}（末尾セグメント無し）は
     * このマッチャの対象外なので、そちらだけでは修飾の欠落を検出できない。
     */
    @Test
    @DisplayName("#281b /files/{id} への GET 以外は認証が必要（permitAllはGET限定）")
    void ファイルパスへの書き込みは要認証() throws Exception {
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                  "/api/v1/files/{id}", 1))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("認可（403）")
  class Forbidden {

    /**
     * #282 他人の投稿は削除できない。
     *
     * <p>この403は {@code ForbiddenException} → {@code GlobalExceptionHandler} 由来で、 {@code
     * RestAccessDeniedHandler}（フィルタチェーン由来）ではない。 {@code SecurityConfig} はロール制御を持たない（{@code
     * anyRequest().authenticated()} のみ）ため、 <b>後者は現状到達不能</b>。
     */
    @Test
    @DisplayName("#282 他人の投稿を削除すると403 FORBIDDEN")
    void 他人の投稿は削除できない() throws Exception {
      long owner = fixtures.user("alice");
      long other = fixtures.user("bob");
      long postId = fixtures.post(owner, "他人の投稿");

      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      "/api/v1/posts/{id}", postId)
                  .with(TestAuth.as(other)))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("FORBIDDEN"))
          .andExpect(jsonPath("$.message").value("この操作を行う権限がありません"));
    }

    /** #283 自分の投稿は削除できる（#282と対にして、403が権限由来であることを示す）。 */
    @Test
    @DisplayName("#283 自分の投稿は削除できる（204）")
    void 自分の投稿は削除できる() throws Exception {
      long owner = fixtures.user("alice");
      long postId = fixtures.post(owner, "自分の投稿");

      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      "/api/v1/posts/{id}", postId)
                  .with(TestAuth.as(owner)))
          .andExpect(status().isNoContent());
    }

    /**
     * #284 存在しない投稿は403ではなく404（D-14の順序）。
     *
     * <p>逆にすると「403が返った＝その投稿は実在する」と分かり、IDの総当たりで 見えないはずの投稿の存在を推測できてしまう。
     */
    @Test
    @DisplayName("#284 存在しない投稿の削除は404（403ではない）")
    void 存在しない投稿は404() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                      "/api/v1/posts/{id}", 999999)
                  .with(TestAuth.as(me)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
  }
}
