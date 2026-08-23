package com.example.snstimeline.common;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * 前後の空白をトリムする文字列デシリアライザ（docs/05_api_design.md 8章「文字列は前後の空白をトリムしてから検証する」）。
 *
 * <p>デシリアライズ時にトリムするのが重要。Spring は「ボディのデシリアライズ→@Valid」の順で 処理するため、ここでトリムすれば {@code " "} が {@code ""}
 * になって @NotBlank が正しく効き、 検証した文字列と保存する文字列が一致する。
 *
 * <p><b>パスワードには適用しない。</b> 前後の空白も正当なパスワード文字であり、黙って除去すると
 * ユーザーが正しく入力したパスワードでログインできなくなる（docs/09_decision_log.md D-27）。
 */
public class TrimDeserializer extends StdDeserializer<String> {

  public TrimDeserializer() {
    super(String.class);
  }

  @Override
  public String deserialize(JsonParser parser, DeserializationContext context) {
    String value = parser.getValueAsString();
    // strip() は Unicode の空白全般を除去する（trim() は U+0020 以下のみ）
    return value == null ? null : value.strip();
  }
}
