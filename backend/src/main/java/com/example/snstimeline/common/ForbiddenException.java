package com.example.snstimeline.common;

/**
 * 403 Forbidden。認証済みだが他人のリソースを操作しようとした場合。
 *
 * <p>認可は「① 存在チェック→404 → ② 所有者チェック→403」の順で行う。 逆にすると存在しないIDに403を返してしまい、リソースの存在有無が漏れる
 * （docs/09_decision_log.md D-14）。
 */
public class ForbiddenException extends ApiException {

  public ForbiddenException() {
    super(ErrorCode.FORBIDDEN);
  }
}
