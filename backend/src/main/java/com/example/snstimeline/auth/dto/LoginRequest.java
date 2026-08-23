package com.example.snstimeline.auth.dto;

import com.example.snstimeline.common.TrimDeserializer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #2 POST /auth/login のリクエスト（docs/05_api_design.md #2）。
 *
 * <p>パスワードには @Pattern を付けない。ログイン画面でパスワードポリシーを 漏らさないため（docs/03_screen_design.md SC-01
 * のバリデーションは「必須」のみ）。
 */
public record LoginRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        String email,
    @NotBlank(message = "パスワードを入力してください") String password) {}
