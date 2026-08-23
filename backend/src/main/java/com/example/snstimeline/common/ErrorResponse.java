package com.example.snstimeline.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 統一エラーレスポンス（docs/05_api_design.md 1.3）。
 *
 * <p>errors はバリデーションエラー時のみ。それ以外ではキー自体を出さない。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String timestamp,
    int status,
    String code,
    String message,
    String path,
    List<FieldErrorItem> errors) {

  public static ErrorResponse of(
      ErrorCode code, String message, String path, List<FieldErrorItem> errors) {
    return new ErrorResponse(
        // 設計書の例は 2026-08-17T12:34:56Z 形式。既定の Instant.toString() は
        // ミリ秒が付くことがあるため秒に丸める。
        DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
        code.getStatus().value(),
        code.name(),
        message,
        path,
        (errors == null || errors.isEmpty()) ? null : errors);
  }

  public static ErrorResponse of(ErrorCode code, String path) {
    return of(code, code.getDefaultMessage(), path, null);
  }
}
