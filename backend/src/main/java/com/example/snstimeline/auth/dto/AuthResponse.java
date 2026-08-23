package com.example.snstimeline.auth.dto;

import com.example.snstimeline.user.dto.UserSummary;

/**
 * #1 signup / #2 login 共通のレスポンス（docs/05_api_design.md #1, #2）。
 *
 * <p>登録と同時にトークンを返すことで、クライアントはログイン画面を経由せず SC-03 へ遷移できる（F-AU-01）。
 *
 * @param accessToken 短命（15分）のJWT。以後のAPI呼び出しで {@code Authorization: Bearer} に載せる
 * @param refreshToken 長命（14日）の不透明トークン。アクセストークンの再発行にのみ使う
 * @param expiresIn アクセストークンの残り有効秒数。クライアントが再発行の タイミングを決めるために返す（JWTをデコードさせずに済ませるため）
 */
public record AuthResponse(
    String accessToken, String refreshToken, long expiresIn, UserSummary user) {}
