package com.example.snstimeline.common.logging;

import org.slf4j.MDC;

/**
 * MDCの読み出し口（docs/12_logging_and_operations.md 4章）。
 *
 * <p>MDCのキー名（文字列リテラル）をこのクラスに閉じ込める。{@code MDC.get("requestId")} を各所に
 * 散らすと、キー名を変更したときに全箇所を洗い出す必要が出るため（frontend の {@code tokenStorage.ts} が localStorage
 * のキーを1箇所に閉じ込めているのと同じ発想）。
 *
 * <p>書き込み（{@code MDC.put}）は {@link RequestIdFilter} / {@link UserIdFilter} だけが行う。 このクラスは読み出し専用。
 */
public final class RequestContext {

  /** MDCキー名。ECS構造化ログでは、MDCの中身がそのままトップレベルのフィールドとして出力される。 */
  static final String REQUEST_ID_KEY = "requestId";

  static final String USER_ID_KEY = "userId";

  private RequestContext() {}

  /**
   * 現在のリクエストIDを返す。
   *
   * @return {@link RequestIdFilter} を通っていれば発番されたID。フィルタを経由しない文脈（単体テスト等）では null
   */
  public static String currentRequestId() {
    return MDC.get(REQUEST_ID_KEY);
  }
}
