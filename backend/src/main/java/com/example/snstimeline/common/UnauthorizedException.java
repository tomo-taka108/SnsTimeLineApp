package com.example.snstimeline.common;

/** 401 Unauthorized。認証失敗（INVALID_CREDENTIALS / UNAUTHENTICATED）。 */
public class UnauthorizedException extends ApiException {

  public UnauthorizedException(ErrorCode errorCode) {
    super(errorCode);
  }
}
