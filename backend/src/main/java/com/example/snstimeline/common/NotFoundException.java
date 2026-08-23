package com.example.snstimeline.common;

/**
 * 404 Not Found。リソースが存在しない、または論理削除済み。
 *
 * <p>認可は「存在チェック→404、所有者チェック→403」の順で行う（docs/09_decision_log.md D-14）。
 */
public class NotFoundException extends ApiException {

  public NotFoundException() {
    super(ErrorCode.NOT_FOUND);
  }
}
