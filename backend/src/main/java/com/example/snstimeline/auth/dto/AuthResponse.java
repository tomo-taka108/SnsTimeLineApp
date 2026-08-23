package com.example.snstimeline.auth.dto;

import com.example.snstimeline.user.dto.UserSummary;

/**
 * #1 signup / #2 login 共通のレスポンス（docs/05_api_design.md #1, #2）。
 *
 * <p>登録と同時にJWTを返すことで、クライアントはログイン画面を経由せず SC-03 へ遷移できる（F-AU-01）。
 */
public record AuthResponse(String token, UserSummary user) {}
