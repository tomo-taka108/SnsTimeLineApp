package com.example.snstimeline.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * #4 トークン再発行のリクエスト（docs/05_api_design.md #4）。
 *
 * <p>リフレッシュトークンは**トリムしない**。ランダム生成のBase64文字列であり、 前後に空白が入ることは想定していない。トリムすると不正な値を黙って
 * 正常な値に変えてしまう可能性がある（パスワードと同じ考え方。D-27）。
 */
public record RefreshRequest(@NotBlank(message = "リフレッシュトークンを指定してください") String refreshToken) {}
