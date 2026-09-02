package com.example.snstimeline.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link CursorCodec} の単体テスト（docs/11_test_design.md 1章、ケース #1〜#19）。
 *
 * <p>DBもモックも不要な純粋な入出力の検証。{@code @DisplayName} の先頭に設計書のケース番号を 入れてあるので、失敗したテストがどのケースかを表から引ける。
 */
class CursorCodecTest {

  /** 設計書の例に合わせた基準時刻（docs/05_api_design.md 2.1）。 */
  private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-08-17T12:34:56.123456Z");

  @Nested
  @DisplayName("encode → decode の往復（正常系）")
  class RoundTrip {

    @Test
    @DisplayName("#1 基準となる時刻とIDが往復で保存される")
    void 基準値が往復する() {
      String encoded = CursorCodec.encode(BASE, 1234L);

      CursorCodec.Cursor decoded = CursorCodec.decode(encoded);

      assertThat(decoded.createdAt()).isEqualTo(BASE);
      assertThat(decoded.id()).isEqualTo(1234L);
    }

    /**
     * #2〜#4 が D-33 の回帰テスト。
     *
     * <p>秒に丸める実装に戻すと、ここが赤くなる。丸めた値は真の値より小さくなるため、 行値比較 {@code (created_at, id) < (cursor)}
     * で同一秒内の投稿が「カーソルより古くない」と 判定されて次ページから漏れる。
     */
    @ParameterizedTest(name = "#2〜#4 マイクロ秒 {0} が丸められずに往復する")
    @ValueSource(strings = {"000001", "999999", "000000"})
    @DisplayName("#2〜#4 マイクロ秒が切り捨てられない（D-33 回帰）")
    void マイクロ秒が保持される(String micros) {
      OffsetDateTime time = OffsetDateTime.parse("2026-08-17T12:34:56." + micros + "Z");

      CursorCodec.Cursor decoded = CursorCodec.decode(CursorCodec.encode(time, 1L));

      assertThat(decoded.createdAt()).isEqualTo(time);
    }

    @Test
    @DisplayName("#5 オフセット付きの時刻がUTCに正規化され、同じ瞬間を指す")
    void オフセットがUTCに正規化される() {
      // 日本時間の 21:34:56.123456 は UTC の 12:34:56.123456 と同じ瞬間
      OffsetDateTime jst = OffsetDateTime.parse("2026-08-17T21:34:56.123456+09:00");

      CursorCodec.Cursor decoded = CursorCodec.decode(CursorCodec.encode(jst, 1L));

      // OffsetDateTime.equals はオフセットまで比較し、「同じ瞬間だがオフセット表記が違う」値を
      // 等しいと見なさない。そのため期待値も UTC に揃えて比較する
      assertThat(decoded.createdAt()).isEqualTo(jst.withOffsetSameInstant(ZoneOffset.UTC));
      // 表記に依らず同じ瞬間を指していることも確認する
      assertThat(decoded.createdAt().toInstant()).isEqualTo(jst.toInstant());
    }

    @Test
    @DisplayName("#6 id が Long.MAX_VALUE でも往復する")
    void 最大のidが往復する() {
      CursorCodec.Cursor decoded = CursorCodec.decode(CursorCodec.encode(BASE, Long.MAX_VALUE));

      assertThat(decoded.id()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("#7 id が 1（最小のBIGSERIAL）でも往復する")
    void 最小のidが往復する() {
      CursorCodec.Cursor decoded = CursorCodec.decode(CursorCodec.encode(BASE, 1L));

      assertThat(decoded.id()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("URLセーフ性")
  class UrlSafety {

    /**
     * #8 標準Base64の {@code +} はクエリ文字列で空白に化け、カーソルが壊れる（docs/05_api_design.md 2.1）。
     *
     * <p><b>1ケースでは検証にならない。</b> たまたま {@code +} が出ない入力を選んでしまう可能性があるため、 時刻とidを変えて多数試す。標準Base64なら
     * {@code +} か {@code /} が出る入力が この範囲に必ず含まれる。
     */
    @ParameterizedTest(name = "#8 id={0} でも + と / が現れない")
    @ValueSource(longs = {1L, 63L, 64L, 255L, 1234L, 65535L, 1_000_000L, Long.MAX_VALUE})
    @DisplayName("#8 URLセーフBase64を使う（+ と / が出ない）")
    void プラスとスラッシュが出ない(long id) {
      for (int micros = 0; micros < 1000; micros += 137) {
        OffsetDateTime time = OffsetDateTime.parse("2026-08-17T12:34:56.%06dZ".formatted(micros));

        String encoded = CursorCodec.encode(time, id);

        assertThat(encoded).doesNotContain("+").doesNotContain("/");
      }
    }

    @Test
    @DisplayName("#9 パディングの = が付かない")
    void パディングが付かない() {
      // 長さが4の倍数にならない入力でもパディングされないこと
      assertThat(CursorCodec.encode(BASE, 1L)).doesNotContain("=");
      assertThat(CursorCodec.encode(BASE, 12L)).doesNotContain("=");
      assertThat(CursorCodec.encode(BASE, 123L)).doesNotContain("=");
    }
  }

  /**
   * decode の失敗経路（デシジョンテーブル、docs/11_test_design.md 1.4）。
   *
   * <pre>
   * ① Base64デコード失敗
   * ② 正規表現 ^\{"c":"([^"]+)","i":(\d+)}$ にマッチしない
   * ③ 日時パース失敗 / Long変換失敗
   * </pre>
   *
   * <p>いずれも 400 VALIDATION_ERROR に潰す。クライアントはカーソルの中身を組み立てないため、
   * デコードできない時点で不正なリクエストである（docs/05_api_design.md 2.1）。
   */
  @Nested
  @DisplayName("decode の失敗経路")
  class DecodeFailure {

    /** JSONをURLセーフBase64にする。壊れたカーソルを組み立てるためのヘルパー。 */
    private String toBase64(String json) {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private void assertRejected(String cursor) {
      assertThatThrownBy(() -> CursorCodec.decode(cursor))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#10 Base64として不正な文字列を拒否する（経路①）")
    void 不正なBase64を拒否する() {
      assertRejected("!!!");
    }

    @Test
    @DisplayName("#11 空文字を拒否する（経路②。デコードは成功し長さ0になる）")
    void 空文字を拒否する() {
      assertRejected("");
    }

    @Test
    @DisplayName("#12 Base64だが中身がJSONでない場合を拒否する（経路②）")
    void JSONでない中身を拒否する() {
      assertRejected(toBase64("hello"));
    }

    @Test
    @DisplayName("#13 キー名が違うJSONを拒否する（経路②）")
    void キー名違いを拒否する() {
      assertRejected(toBase64("{\"x\":\"2026-08-17T12:34:56.123456Z\",\"i\":1}"));
    }

    /**
     * #14 正規表現の {@code (\d+)} は符号を含まないため、負のidは経路②で弾かれる。
     *
     * <p>id は BIGSERIAL の単調増加で負にならない（docs/04_data_model.md 2.1）。 表を作って初めて出てきたケース。
     */
    @Test
    @DisplayName("#14 id が負数のカーソルを拒否する（経路②）")
    void 負のidを拒否する() {
      assertRejected(toBase64("{\"c\":\"2026-08-17T12:34:56.123456Z\",\"i\":-1}"));
    }

    @Test
    @DisplayName("#15 前後に空白が付いたカーソルを拒否する（経路①）")
    void 前後の空白を拒否する() {
      assertRejected(" " + CursorCodec.encode(BASE, 1L));
    }

    @Test
    @DisplayName("#16 日時としてありえない値を拒否する（経路③・日時）")
    void 不正な日時を拒否する() {
      assertRejected(toBase64("{\"c\":\"2026-13-45T99:99:99.000000Z\",\"i\":1}"));
    }

    @Test
    @DisplayName("#17 id が long に収まらない場合を拒否する（経路③・Long）")
    void long超過のidを拒否する() {
      assertRejected(
          toBase64("{\"c\":\"2026-08-17T12:34:56.123456Z\",\"i\":99999999999999999999}"));
    }

    /** #18 正規表現の {@code $} が末尾を固定していること。表を作って初めて出てきたケース。 */
    @Test
    @DisplayName("#18 末尾に余分な文字が付いたJSONを拒否する（経路②）")
    void 末尾の余分な文字を拒否する() {
      assertRejected(toBase64("{\"c\":\"2026-08-17T12:34:56.123456Z\",\"i\":1}x"));
    }

    /**
     * #19 <b>現在の挙動を固定するテスト（docs/11_test_design.md 1.4）。</b>
     *
     * <p>{@code Base64.getUrlDecoder().decode(null)} は {@code IllegalArgumentException} ではなく {@code
     * NullPointerException} を投げるため、{@code CursorCodec} の {@code catch} に捕まらず 500 になる。
     *
     * <p><b>ただし実害は無い。</b> {@code decode} の呼び出し元5箇所すべてが {@code cursor == null ? null :
     * decode(cursor)} の形で null を除いているため、null が到達しない （CommentService:47 / FollowService:81,97 /
     * PostService:59,100）。
     *
     * <p>このテストは「直すべき」と主張するものではなく、<b>現状こうなっているという記録</b>である。 null ガードを足して 400 に変える場合は、このテストを書き換えること。
     */
    @Test
    @DisplayName("#19 null は ApiException ではなく NPE になる（現状の記録。呼び出し元が null を除くため実害なし）")
    void nullは現状NPEになる() {
      Throwable thrown = catchThrowable(() -> CursorCodec.decode(null));

      assertThat(thrown).isInstanceOf(NullPointerException.class);
      assertThat(thrown).isNotInstanceOf(ApiException.class);
    }
  }
}
