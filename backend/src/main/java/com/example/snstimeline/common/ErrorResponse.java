package com.example.snstimeline.common;

import com.example.snstimeline.common.logging.RequestContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 統一エラーレスポンス（docs/05_api_design.md 1.3）。
 *
 * <p>errors はバリデーションエラー時のみ。それ以外ではキー自体を出さない。
 *
 * <p>{@code requestId} は {@link RequestContext#currentRequestId()} から読む
 * （docs/12_logging_and_operations.md 4章、D-63）。ユーザーが見たエラー画面と サーバーログの該当行を、この値で突き合わせられる。フィルタを経由しない文脈
 * （単体テスト等）では null になり、{@code @JsonInclude(NON_NULL)} によりキー自体が消える。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "エラー時の共通レスポンス。すべてのエラーがこの形式で返る")
public record ErrorResponse(
    @Schema(description = "エラー発生日時（ISO 8601 / UTC）", example = "2026-08-17T12:34:56Z")
        String timestamp,
    @Schema(description = "HTTPステータスコード", example = "400") int status,
    @Schema(description = "アプリケーション定義のエラーコード。クライアントはこの値で分岐する", example = "VALIDATION_ERROR")
        String code,
    @Schema(description = "ユーザーに表示可能な日本語メッセージ", example = "入力内容に誤りがあります") String message,
    @Schema(description = "リクエストパス", example = "/api/v1/posts") String path,
    @Schema(description = "フィールド単位のエラー。バリデーションエラー時のみ含まれる") List<FieldErrorItem> errors,
    @Schema(
            description = "サーバーログと突き合わせるためのリクエストID。レスポンスヘッダ X-Request-Id と同じ値",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        String requestId) {

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
        (errors == null || errors.isEmpty()) ? null : errors,
        RequestContext.currentRequestId());
  }

  public static ErrorResponse of(ErrorCode code, String path) {
    return of(code, code.getDefaultMessage(), path, null);
  }
}
