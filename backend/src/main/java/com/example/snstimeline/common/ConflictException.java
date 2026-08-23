package com.example.snstimeline.common;

/** 409 Conflict。一意制約違反（メール・ユーザー名の重複）。 */
public class ConflictException extends ApiException {

  public ConflictException(ErrorCode errorCode) {
    super(errorCode);
  }
}
