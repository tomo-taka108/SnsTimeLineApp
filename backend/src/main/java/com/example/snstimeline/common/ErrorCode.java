package com.example.snstimeline.common;

import org.springframework.http.HttpStatus;

/**
 * APIのエラーコード（docs/05_api_design.md 1.3）。
 *
 * <p>今回の認証スコープで到達しないコードも含め、設計書の11種をすべて定義する。 エラーコードの一覧そのものがAPIの契約であるため。
 */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "入力内容に誤りがあります"),
  SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "自分自身をフォローすることはできません"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "メールアドレスまたはパスワードが正しくありません"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "認証が必要です"),
  // 無効・期限切れ・使用済み・失効済みをすべてこの1コードにまとめる。
  // 理由を返すと、攻撃者に「そのトークンは存在するが失効済み」といった情報を与えるため
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "セッションの有効期限が切れました。再度ログインしてください"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "この操作を行う権限がありません"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "リソースが見つかりません"),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "このメールアドレスは既に登録されています"),
  USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "このユーザー名は既に使われています"),
  // Spring 7 で PAYLOAD_TOO_LARGE は非推奨（CONTENT_TOO_LARGE に改称）。ステータス値 413 は同じ
  FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "ファイルサイズが大きすぎます"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "対応していないファイル形式です"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "サーバーエラーが発生しました");

  private final HttpStatus status;
  private final String defaultMessage;

  ErrorCode(HttpStatus status, String defaultMessage) {
    this.status = status;
    this.defaultMessage = defaultMessage;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getDefaultMessage() {
    return defaultMessage;
  }
}
