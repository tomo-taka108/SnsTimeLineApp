package com.example.snstimeline.auth.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #1 POST /auth/signup のリクエスト（docs/05_api_design.md #1）。
 *
 * <p>エラーメッセージは docs/03_screen_design.md SC-02 の文言をそのまま使う。
 */
public record SignupRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "メールアドレスを入力してください")
        @Email(message = "メールアドレスの形式が正しくありません")
        @Size(max = ValidationConstants.EMAIL_MAX, message = "メールアドレスは255文字以内で入力してください")
        String email,
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "ユーザー名を入力してください")
        @Size(
            min = ValidationConstants.USERNAME_MIN,
            max = ValidationConstants.USERNAME_MAX,
            message = "ユーザー名は3〜30文字で入力してください")
        @Pattern(
            regexp = ValidationConstants.USERNAME_PATTERN,
            message = "ユーザー名は半角英数字とアンダースコアのみ使用できます")
        String username,
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "表示名を入力してください")
        @CodePointLength(
            min = ValidationConstants.DISPLAY_NAME_MIN,
            max = ValidationConstants.DISPLAY_NAME_MAX,
            message = "表示名は1〜50文字で入力してください")
        String displayName,
    // パスワードはトリムしない（docs/09_decision_log.md D-27）。
    // @Size(min=8) を併記すると1フィールドに2件エラーが出るため @Pattern 1本にする。
    @JsonProperty("password")
        @NotBlank(message = "パスワードを入力してください")
        @Pattern(
            regexp = ValidationConstants.PASSWORD_PATTERN,
            message = "パスワードは8文字以上で、英字と数字を含めてください")
        String password) {}
