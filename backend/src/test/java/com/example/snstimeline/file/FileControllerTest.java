package com.example.snstimeline.file;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 画像APIの結合テスト（docs/11_test_design.md 20章、ケース #320〜#323）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FileControllerTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TestFixtures fixtures;

  /** PNGのマジックバイト（{@code ImageTypeTest} と同じ値）。 */
  private static final byte[] PNG_BYTES = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
  };

  /** #320 アップロードは201で、fileId と配信URLが返る。 */
  @Test
  @DisplayName("#320 画像のアップロードは201でURLが返る")
  void アップロード() throws Exception {
    long me = fixtures.user("alice");
    MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);

    mvc.perform(multipart("/api/v1/files").file(file).with(TestAuth.as(me)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.fileId").isNumber())
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/v1/files/")));
  }

  /**
   * #321 必須項目「画像のマジックバイト検証」を<b>HTTP経由で</b>確認する。
   *
   * <p>{@code image/png} と名乗ったテキストは 415 で拒否される。 拡張子・Content-Typeだけを見ていないことの確認（D-42）。
   */
  @Test
  @DisplayName("#321 image/pngと名乗ったテキストは415")
  void 偽装ファイルは415() throws Exception {
    long me = fixtures.user("alice");
    MockMultipartFile fake =
        new MockMultipartFile("file", "fake.png", "image/png", "これはテキストです".getBytes());

    mvc.perform(multipart("/api/v1/files").file(fake).with(TestAuth.as(me)))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  /**
   * #322 必須項目「画像配信の認証要否」。
   *
   * <p>配信は認証不要（{@code <img src>} は Authorization ヘッダを送れないため）。 併せて {@code Cache-Control} が付くことも確認する
   * （06_non_functional.md 1.3、再取得を防ぐ）。
   */
  @Test
  @DisplayName("#322 画像配信は認証不要で、Cache-Controlが付く")
  void 配信は認証不要でキャッシュ可能() throws Exception {
    long me = fixtures.user("alice");
    MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);
    String body =
        mvc.perform(multipart("/api/v1/files").file(file).with(TestAuth.as(me)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    int fileId = com.jayway.jsonpath.JsonPath.read(body, "$.fileId");

    // 認証を付けずに取得できること
    mvc.perform(get("/api/v1/files/{id}", fileId))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
        .andExpect(header().string("Content-Type", "image/png"));
  }

  /** #323 存在しないファイルの配信は404。 */
  @Test
  @DisplayName("#323 存在しないファイルの配信は404")
  void 存在しないファイルは404() throws Exception {
    mvc.perform(get("/api/v1/files/{id}", 999999))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
