package com.example.snstimeline.comment.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #12 PATCH /comments/{commentId} のリクエスト（docs/05_api_design.md #12）。
 *
 * <p>コメント編集（F-CM-03）はdocs上はPhase2だが、今回MVPへ前倒しした （docs/09_decision_log.md D-51）。
 *
 * <p>min を {@code @CodePointLength} に併記しない。{@code @NotBlank} がトリム後の空文字を弾くため （{@code
 * CreateCommentRequest} と同じ理由）。
 */
public record UpdateCommentRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "コメントを入力してください")
        @CodePointLength(
            max = ValidationConstants.COMMENT_BODY_MAX,
            message = "コメントは280文字以内で入力してください")
        // 独自制約 @CodePointLength は springdoc が自動認識しないため、上限をここで明示する
        @Schema(description = "コメントの本文。280文字以内（絵文字は1文字と数える）", example = "コメントを修正しました")
        String body) {}
