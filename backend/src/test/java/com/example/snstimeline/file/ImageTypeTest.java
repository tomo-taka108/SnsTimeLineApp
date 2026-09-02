package com.example.snstimeline.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ImageType} の単体テスト（docs/11_test_design.md 3章、ケース #31〜#53）。
 *
 * <p>Content-Type ヘッダは送信側が自由に名乗れるため、それだけでは検証にならない。 {@code .jpg} にリネームしただけのファイルを弾くために、実体の先頭バイト
 * （マジックナンバー）が名乗った形式と一致することを確認する（docs/06_non_functional.md 3.5、D-42）。
 */
class ImageTypeTest {

  private static final byte[] JPEG_HEADER = bytes(0xFF, 0xD8, 0xFF);
  private static final byte[] PNG_HEADER = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);

  /** int の並びを byte[] にする。0xFF のような値を byte リテラルで書くと符号付きで読みにくいため。 */
  private static byte[] bytes(int... values) {
    byte[] result = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (byte) values[i];
    }
    return result;
  }

  /**
   * WebP のバイト列を組み立てる。
   *
   * <p>RIFF コンテナのため、先頭4バイトが {@code RIFF}、4〜7バイト目が<b>ファイルサイズ（値は不定）</b>、 8バイト目から {@code WEBP}
   * が続く。サイズ部分に何が入っていても判定に影響しないことを示すため、 呼び出し側から任意の値を渡せるようにする。
   */
  private static byte[] webp(String tagAt8, int... sizeBytes) {
    byte[] riff = "RIFF".getBytes(StandardCharsets.US_ASCII);
    byte[] size = bytes(sizeBytes);
    byte[] tag = tagAt8.getBytes(StandardCharsets.US_ASCII);

    byte[] result = new byte[riff.length + size.length + tag.length];
    System.arraycopy(riff, 0, result, 0, riff.length);
    System.arraycopy(size, 0, result, riff.length, size.length);
    System.arraycopy(tag, 0, result, riff.length + size.length, tag.length);
    return result;
  }

  @Nested
  @DisplayName("fromContentType（Content-Type 名から形式を引く）")
  class FromContentType {

    @ParameterizedTest(name = "#31〜#33 {0} → {1}")
    @CsvSource({"image/jpeg,JPEG", "image/png,PNG", "image/webp,WEBP"})
    @DisplayName("#31〜#33 対応する Content-Type から形式を引ける")
    void 対応形式を引ける(String contentType, ImageType expected) {
      assertThat(ImageType.fromContentType(contentType)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "#34 {0} → JPEG（大小を無視する）")
    @ValueSource(strings = {"IMAGE/JPEG", "Image/Jpeg", "image/JPEG"})
    @DisplayName("#34 Content-Type の大小文字を無視する")
    void 大小文字を無視する(String contentType) {
      assertThat(ImageType.fromContentType(contentType)).isEqualTo(ImageType.JPEG);
    }

    @ParameterizedTest(name = "#35〜#36 {0} → null")
    @ValueSource(strings = {"image/gif", "image/bmp", "text/plain", "application/octet-stream"})
    @DisplayName("#35〜#36 未対応の形式は null を返す")
    void 未対応形式はnull(String contentType) {
      assertThat(ImageType.fromContentType(contentType)).isNull();
    }

    @ParameterizedTest(name = "#37〜#38 null / 空文字 → null")
    @NullAndEmptySource
    @DisplayName("#37〜#38 null と空文字は null を返す（NPEにしない）")
    void nullと空文字はnull(String contentType) {
      assertThat(ImageType.fromContentType(contentType)).isNull();
    }
  }

  @Nested
  @DisplayName("matches（マジックバイトの照合）")
  class Matches {

    @Test
    @DisplayName("#39 JPEG のヘッダに続きがあっても一致する")
    void JPEGが一致する() {
      byte[] content = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10);

      assertThat(ImageType.JPEG.matches(content)).isTrue();
    }

    @Test
    @DisplayName("#40 JPEG の3バイト目が違えば一致しない")
    void JPEGの3バイト目違いを弾く() {
      assertThat(ImageType.JPEG.matches(bytes(0xFF, 0xD8, 0xFE))).isFalse();
    }

    @Test
    @DisplayName("#41 JPEG に必要な3バイトに1つ足りなければ一致しない")
    void JPEGの長さ不足を弾く() {
      assertThat(ImageType.JPEG.matches(bytes(0xFF, 0xD8))).isFalse();
    }

    @Test
    @DisplayName("#42 JPEG のヘッダちょうど3バイトで一致する")
    void JPEGのちょうどの長さで一致する() {
      assertThat(ImageType.JPEG.matches(JPEG_HEADER)).isTrue();
    }

    @Test
    @DisplayName("#43 空のバイト列はどの形式にも一致しない")
    void 空のバイト列を弾く() {
      byte[] empty = new byte[0];

      assertThat(ImageType.JPEG.matches(empty)).isFalse();
      assertThat(ImageType.PNG.matches(empty)).isFalse();
      assertThat(ImageType.WEBP.matches(empty)).isFalse();
    }

    @Test
    @DisplayName("#44 PNG の8バイトのヘッダが一致する")
    void PNGが一致する() {
      assertThat(ImageType.PNG.matches(PNG_HEADER)).isTrue();
    }

    @Test
    @DisplayName("#45 PNG に必要な8バイトに1つ足りなければ一致しない")
    void PNGの長さ不足を弾く() {
      byte[] sevenBytes = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A);

      assertThat(ImageType.PNG.matches(sevenBytes)).isFalse();
    }

    /** #46 4〜7バイト目はファイルサイズで値が定まらないため、何が入っていても判定に影響しないこと。 */
    @Test
    @DisplayName("#46 WebP は RIFF + 任意の4バイト + WEBP で一致する")
    void WEBPが一致する() {
      assertThat(ImageType.WEBP.matches(webp("WEBP", 0x00, 0x00, 0x00, 0x00))).isTrue();
      assertThat(ImageType.WEBP.matches(webp("WEBP", 0xFF, 0xFF, 0xFF, 0xFF))).isTrue();
      assertThat(ImageType.WEBP.matches(webp("WEBP", 0x1A, 0x2B, 0x3C, 0x4D))).isTrue();
    }

    /**
     * #47 <b>「先頭4バイトだけ見る実装」に退化したら赤くなるテスト。</b>
     *
     * <p>RIFF は WebP 専用のコンテナではない（WAV や AVI も RIFF）。 8バイト目からの {@code WEBP} まで見ないと、WAVファイルを画像として
     * 受け入れてしまう。
     */
    @Test
    @DisplayName("#47 RIFF で始まっても8バイト目が WEBP でなければ一致しない")
    void RIFFだけでは一致しない() {
      assertThat(ImageType.WEBP.matches(webp("XXXX", 0x00, 0x00, 0x00, 0x00))).isFalse();
      // 実在の例: WAV も RIFF コンテナ
      assertThat(ImageType.WEBP.matches(webp("WAVE", 0x00, 0x00, 0x00, 0x00))).isFalse();
    }

    @Test
    @DisplayName("#48 RIFF の4バイトだけでは一致しない")
    void RIFFの4バイトだけを弾く() {
      byte[] riffOnly = "RIFF".getBytes(StandardCharsets.US_ASCII);

      assertThat(ImageType.WEBP.matches(riffOnly)).isFalse();
    }

    @Test
    @DisplayName("#49 WebP に必要な12バイトに1つ足りなければ一致しない")
    void WEBPの長さ不足を弾く() {
      byte[] elevenBytes = new byte[11];
      System.arraycopy(webp("WEBP", 0x00, 0x00, 0x00, 0x00), 0, elevenBytes, 0, 11);

      assertThat(ImageType.WEBP.matches(elevenBytes)).isFalse();
    }
  }

  /**
   * 名乗った Content-Type と実体の不一致（docs/11_test_design.md 3.4）。
   *
   * <p><b>#31〜#49 は部品の確認で、ここが攻撃シナリオそのもの。</b> なお 415 を返すのは {@code FileService} なので、ここでは {@code
   * matches} が false になることまでを確認する。 415 の確認は {@code FileServiceTest}（Stage 1 後半）で行う。
   */
  @Nested
  @DisplayName("名乗りと実体のクロスチェック")
  class CrossCheck {

    @Test
    @DisplayName("#50 image/jpeg を名乗り実体もJPEGなら通過する")
    void 正しい組み合わせは通過する() {
      ImageType declared = ImageType.fromContentType("image/jpeg");

      assertThat(declared).isNotNull();
      assertThat(declared.matches(JPEG_HEADER)).isTrue();
    }

    /** #51 docs/06_non_functional.md 5.3 の必須項目「.jpg にリネームしたテキストファイルが拒否される」。 */
    @Test
    @DisplayName("#51 image/jpeg を名乗る実体がテキストなら拒否する")
    void テキストをJPEGと名乗っても拒否する() {
      byte[] text = "hello".getBytes(StandardCharsets.UTF_8);
      ImageType declared = ImageType.fromContentType("image/jpeg");

      assertThat(declared).isNotNull();
      assertThat(declared.matches(text)).isFalse();
    }

    @Test
    @DisplayName("#52 image/jpeg を名乗る実体がPNGなら拒否する")
    void PNGをJPEGと名乗っても拒否する() {
      ImageType declared = ImageType.fromContentType("image/jpeg");

      assertThat(declared).isNotNull();
      assertThat(declared.matches(PNG_HEADER)).isFalse();
    }

    @Test
    @DisplayName("#53 image/png を名乗る実体がJPEGなら拒否する")
    void JPEGをPNGと名乗っても拒否する() {
      ImageType declared = ImageType.fromContentType("image/png");

      assertThat(declared).isNotNull();
      assertThat(declared.matches(JPEG_HEADER)).isFalse();
    }
  }
}
