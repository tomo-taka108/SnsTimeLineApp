package com.example.snstimeline.common;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * カーソルのエンコード・デコード（docs/05_api_design.md 2.1、docs/09_decision_log.md D-06）。
 *
 * <p>{@code {"c":"<ISO8601>","i":<id>}} を Base64 にした不透明な文字列。 <b>クライアントは中身を解釈してはならない。</b>
 * 将来ソートキーを変えてもクライアントを 修正せずに済むようにするため。
 *
 * <p><b>時刻はマイクロ秒精度で保持する（D-33）。</b> 設計当初は秒精度としていたが、 秒に丸めると行値比較 {@code (created_at, id) < (cursor)}
 * で同一秒内の投稿を取りこぼす。 丸めた値は真の値より小さくなるため、同じ秒に投稿された行が「カーソルより古くない」と 判定されて次ページから漏れる。25件を一括投入すると再現する。
 *
 * <p>Base64は<b>URLセーフ版</b>を使う。標準Base64の {@code +} はクエリ文字列で 空白に解釈され、カーソルが壊れるため。
 */
public final class CursorCodec {

  private CursorCodec() {}

  /** カーソルが指す位置。ソートキー {@code (created_at DESC, id DESC)} に対応する。 */
  public record Cursor(OffsetDateTime createdAt, Long id) {}

  /**
   * 手組みのJSONを読むための正規表現。
   *
   * <p>Jacksonを通さないのは、この2フィールドだけの固定構造にパーサを持ち込む必要が無いため。 想定外の文字列は必ずマッチしないので、不正なカーソルの検出も兼ねる。
   */
  private static final Pattern CURSOR_PATTERN =
      Pattern.compile("^\\{\"c\":\"([^\"]+)\",\"i\":(\\d+)}$");

  /** マイクロ秒までのISO8601。UTC固定（末尾Z）で、環境のタイムゾーンに依存させない。 */
  private static final DateTimeFormatter FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'");

  /** ソートキーの組をカーソル文字列にする。 */
  public static String encode(OffsetDateTime createdAt, Long id) {
    String json =
        "{\"c\":\""
            + FORMATTER.format(createdAt.withOffsetSameInstant(ZoneOffset.UTC))
            + "\",\"i\":"
            + id
            + "}";
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * カーソル文字列をソートキーの組に戻す。
   *
   * <p>壊れた値・改竄された値はすべて 400 にする。クライアントが中身を組み立てることは 想定していないため、デコードできない時点で不正なリクエストである。
   *
   * @throws ApiException 復号・パースに失敗した場合（VALIDATION_ERROR → 400）
   */
  public static Cursor decode(String cursor) {
    String json;
    try {
      json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }

    Matcher matcher = CURSOR_PATTERN.matcher(json);
    if (!matcher.matches()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }

    try {
      OffsetDateTime createdAt =
          OffsetDateTime.parse(matcher.group(1), FORMATTER.withZone(ZoneOffset.UTC));
      return new Cursor(createdAt, Long.valueOf(matcher.group(2)));
    } catch (RuntimeException e) {
      // 日時としてありえない値、または id が long に収まらない場合
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
  }
}
