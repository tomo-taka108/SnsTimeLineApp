package com.example.snstimeline.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 例外を統一エラー形式に変換する（docs/05_api_design.md 1.3、F-CO-01）。
 *
 * <p>注意: 401/403 はフィルタチェーン内で発生するためここでは捕捉できない。 AuthEntryPoint / RestAccessDeniedHandler が担当する。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** バリデーションエラー。フィールド単位のメッセージを errors[] に詰める。 */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest request) {

    List<FieldErrorItem> errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
            .toList();

    // 入力値そのものは出さない（パスワード等が混ざるため）。フィールド名のみ。
    log.warn(
        "バリデーションエラー path={} fields={}",
        request.getRequestURI(),
        errors.stream().map(FieldErrorItem::field).toList());

    return build(
        ErrorCode.VALIDATION_ERROR,
        ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
        request,
        errors);
  }

  /** 壊れたJSON、型の不一致。 */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    log.warn("リクエストボディの解析に失敗 path={}", request.getRequestURI());
    return build(ErrorCode.VALIDATION_ERROR, "リクエストの形式が正しくありません", request, null);
  }

  /**
   * アップロードサイズ超過（#25）。
   *
   * <p>Spring の multipart 制限は Controller に入る前に発火するため、 {@code FileService} のサイズ検証には到達しない。ここで拾わないと
   * 500 になる （docs/06_non_functional.md 3.5 は 413 を要求している）。
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSize(
      MaxUploadSizeExceededException e, HttpServletRequest request) {
    log.warn("アップロードサイズ超過 path={}", request.getRequestURI());
    return build(
        ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getDefaultMessage(), request, null);
  }

  /** パスパラメータ等の型不一致（IDに数値以外が来た場合など）。 */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    return build(ErrorCode.VALIDATION_ERROR, "パラメータの形式が正しくありません", request, null);
  }

  /** 業務例外（409 / 401 / 404 など）。 */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(
      ApiException e, HttpServletRequest request) {
    ErrorCode code = e.getErrorCode();
    if (code.getStatus().is4xxClientError()) {
      log.warn("業務エラー code={} path={}", code.name(), request.getRequestURI());
    }
    return build(code, code.getDefaultMessage(), request, null);
  }

  /** サービス層をすり抜けた一意制約違反の保険。 */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrity(
      DataIntegrityViolationException e, HttpServletRequest request) {
    log.warn("一意制約違反 path={}", request.getRequestURI());
    return build(
        ErrorCode.EMAIL_ALREADY_EXISTS,
        ErrorCode.EMAIL_ALREADY_EXISTS.getDefaultMessage(),
        request,
        null);
  }

  /** 想定外の例外。スタックトレースはサーバー側のログにのみ出す。 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
    // クライアントには汎用メッセージのみ返す（docs/06_non_functional.md 3.8）
    log.error("予期しないエラー path={}", request.getRequestURI(), e);
    return build(
        ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request, null);
  }

  private ResponseEntity<ErrorResponse> build(
      ErrorCode code, String message, HttpServletRequest request, List<FieldErrorItem> errors) {
    return ResponseEntity.status(code.getStatus())
        .body(ErrorResponse.of(code, message, request.getRequestURI(), errors));
  }
}
