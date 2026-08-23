package com.example.snstimeline.common;

/** ErrorCode を持つ業務例外の基底クラス。GlobalExceptionHandler が一括で統一形式に変換する。 */
public class ApiException extends RuntimeException {

  private final ErrorCode errorCode;

  public ApiException(ErrorCode errorCode) {
    super(errorCode.getDefaultMessage());
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
