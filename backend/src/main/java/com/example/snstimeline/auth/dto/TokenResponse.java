package com.example.snstimeline.auth.dto;

/**
 * #4 トークン再発行のレスポンス（docs/05_api_design.md #4）。
 *
 * <p>{@link AuthResponse} と違い {@code user} を含まない。再発行はトークンの差し替えだけが目的で、 ユーザー情報が必要なら {@code GET
 * /auth/me} を呼べばよいため。
 *
 * <p><b>リフレッシュトークンも新しい値に差し替わる（ローテーション）。</b> クライアントは古い値を捨てて、ここで返された値を保存し直すこと。
 */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {}
