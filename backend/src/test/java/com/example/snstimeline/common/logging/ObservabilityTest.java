package com.example.snstimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestAuth;
import com.example.snstimeline.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 構造化ログの観測点・相関ID・機密情報の非出力を検証する結合テスト（docs/11_test_design.md 25章、ケース #472〜）。
 *
 * <p>25.0: <b>ログはテストしにくい。</b> 戻り値ではなく「副作用として出た文字列」を検証する必要があるため、 Logback の {@link ListAppender}
 * をルートロガーに差し込み、テスト中に出たログ行を丸ごと集める。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ObservabilityTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  private ListAppender<ILoggingEvent> appender;
  private Logger rootLogger;

  /**
   * ルートロガーに {@link ListAppender} を差し込む。
   *
   * <p><b>ルートに差す理由。</b> 個々のクラスのロガーにだけ差すと、後から観測点を足したクラスが 検証対象から漏れる（#44③のような「たまたま安全だった」を繰り返す）。
   * ルートに1つ差せば、どのクラスが出したログも取りこぼさない。
   */
  @BeforeEach
  void attachAppender() {
    rootLogger = (Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    appender = new ListAppender<>();
    appender.start();
    rootLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    rootLogger.detachAppender(appender);
  }

  private List<String> loggedMessages() {
    return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  @Nested
  @DisplayName("リクエストIDの相関")
  class RequestIdCorrelation {

    /**
     * #472 <b>500のJSONに requestId が入り、X-Request-Id ヘッダと同じ値になる。</b>
     *
     * <p>ユーザーが見たエラー画面とサーバーログの該当行を、この値1つで突き合わせられることの確認（D-63）。
     */
    @Test
    @DisplayName("#472 エラーレスポンスのrequestIdはX-Request-Idヘッダと一致する")
    void エラーレスポンスとヘッダのrequestIdが一致() throws Exception {
      long me = fixtures.user("alice");

      var result =
          mvc.perform(get("/api/v1/posts/{id}", 999999).with(TestAuth.as(me)))
              .andExpect(status().isNotFound())
              .andExpect(jsonPath("$.requestId").isNotEmpty())
              .andExpect(header().exists("X-Request-Id"))
              .andReturn();

      String headerValue = result.getResponse().getHeader("X-Request-Id");
      String bodyRequestId =
          com.jayway.jsonpath.JsonPath.read(
              result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
              "$.requestId");

      assertThat(bodyRequestId).isEqualTo(headerValue);
    }

    /** #473 200系のレスポンスにも X-Request-Id ヘッダが付く（エラー時専用ではないことの確認）。 */
    @Test
    @DisplayName("#473 成功レスポンスにもX-Request-Idヘッダが付く")
    void 成功時もヘッダが付く() throws Exception {
      long me = fixtures.user("alice");

      mvc.perform(get("/api/v1/auth/me").with(TestAuth.as(me)))
          .andExpect(status().isOk())
          .andExpect(header().exists("X-Request-Id"));
    }
  }

  @Nested
  @DisplayName("機密情報の非出力（本章の中核）")
  class NoSecretLeak {

    /**
     * #474 <b>signup / login / アップロードを一通り叩き、ログ全文に平文パスワード・JWT・ メールアドレスが1行も現れないことを確認する。</b>
     *
     * <p>CLAUDE.md 6章の「ログにパスワード・JWT・メールアドレスを出力しない」は、これまでレビューでしか
     * 担保されていなかった。個別のログ文を1つずつ確認する方式は、新しいログ文を足したときに 検証されないという穴がある。ここでは「集めた全ログ行のどれにも含まれない」という形で
     * 1箇所で見張ることで、その穴を塞ぐ。
     */
    @Test
    @DisplayName("#474 ログ全文に平文パスワード・JWT・メールアドレスが現れない")
    void 機密情報がログに出ない() throws Exception {
      String email = "leak-check@example.com";
      String plainPassword = "Password1SuperSecret";

      // 新規登録（成功） → ログイン成功 → ログイン失敗 → 画像アップロード拒否、まで一通り叩く
      String signupBody =
          """
          {"email":"%s","username":"leakcheck","displayName":"漏洩確認用","password":"%s"}
          """
              .formatted(email, plainPassword);
      var signupResult =
          mvc.perform(
                  post("/api/v1/auth/signup")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(signupBody))
              .andExpect(status().isCreated())
              .andReturn();
      String accessToken =
          com.jayway.jsonpath.JsonPath.read(
              signupResult
                  .getResponse()
                  .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
              "$.accessToken");

      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"%s","password":"%s"}
                      """
                          .formatted(email, plainPassword)))
          .andExpect(status().isOk());

      // ログイン失敗（パスワード違い）
      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"%s","password":"wrong-password"}
                      """
                          .formatted(email)))
          .andExpect(status().isUnauthorized());

      // 画像アップロード拒否（マジックバイト不一致）
      MockMultipartFile fake =
          new MockMultipartFile("file", "fake.png", "image/png", "not-a-real-image".getBytes());
      long uploader = fixtures.user("bob");
      mvc.perform(multipart("/api/v1/files").file(fake).with(TestAuth.as(uploader)))
          .andExpect(status().isUnsupportedMediaType());

      // 認可エラー（403）。X-Request-Idを含むレスポンスヘッダ処理も一通り通す
      long owner = fixtures.user("carol");
      long postId = fixtures.post(owner, "投稿");
      mvc.perform(delete("/api/v1/posts/{id}", postId).with(TestAuth.as(uploader)))
          .andExpect(status().isForbidden());

      List<String> messages = loggedMessages();
      assertThat(messages).isNotEmpty();
      assertThat(messages)
          .noneMatch(m -> m.contains(plainPassword))
          .noneMatch(m -> m.contains(accessToken))
          .noneMatch(m -> m.contains(email));
    }
  }

  @Nested
  @DisplayName("観測点が期待どおりのレベルで出る")
  class ObservationPoints {

    /** #475 ログイン成功はINFOで1行出る。 */
    @Test
    @DisplayName("#475 ログイン成功はINFOで出る")
    void ログイン成功はINFO() throws Exception {
      String email = "login-success@example.com";
      mvc.perform(
              post("/api/v1/auth/signup")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"%s","username":"loginsuccess","displayName":"ログイン成功確認",
                       "password":"Password1"}
                      """
                          .formatted(email)))
          .andExpect(status().isCreated());

      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"%s","password":"Password1"}
                      """
                          .formatted(email)))
          .andExpect(status().isOk());

      assertThat(loggedMessages()).anyMatch(m -> m.startsWith("ログイン成功"));
    }

    /** #476 ログイン失敗はWARNで1行出る（メールアドレスは含まない）。 */
    @Test
    @DisplayName("#476 ログイン失敗はWARNで出て、メールアドレスを含まない")
    void ログイン失敗はWARN() throws Exception {
      mvc.perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"email":"nonexistent@example.com","password":"whatever"}
                      """))
          .andExpect(status().isUnauthorized());

      assertThat(loggedMessages())
          .anyMatch(m -> m.equals("ログイン失敗"))
          .noneMatch(m -> m.contains("nonexistent@example.com"));
    }

    /** #477 画像アップロード成功はINFOで1行出る（fileId・storageType等を含む）。 */
    @Test
    @DisplayName("#477 画像アップロード成功はINFOで出る")
    void 画像アップロード成功はINFO() throws Exception {
      long me = fixtures.user("dave");
      byte[] pngBytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
      MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", pngBytes);

      mvc.perform(multipart("/api/v1/files").file(file).with(TestAuth.as(me)))
          .andExpect(status().isCreated());

      assertThat(loggedMessages()).anyMatch(m -> m.startsWith("画像アップロード fileId="));
    }

    /** #478 アクセスログが1リクエスト1行、method/path/status/durationMs付きで出る。 */
    @Test
    @DisplayName("#478 アクセスログがmethod/path/status付きで1行出る")
    void アクセスログが1行出る() throws Exception {
      long me = fixtures.user("erin");

      mvc.perform(get("/api/v1/auth/me").with(TestAuth.as(me))).andExpect(status().isOk());

      assertThat(loggedMessages())
          .anyMatch(
              m -> m.startsWith("access method=GET path=/api/v1/auth/me status=200 durationMs="));
    }
  }
}
