package com.example.snstimeline.comment.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #11 POST /posts/{postId}/comments のリクエスト（docs/05_api_design.md #11）。
 *
 * <p>min を {@code @CodePointLength} に併記しない。{@code @NotBlank} がトリム後の空文字を弾くため （{@code
 * CreatePostRequest} と同じ理由、docs/05_api_design.md 8章）。
 */
public record CreateCommentRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "コメントを入力してください")
        @CodePointLength(
            max = ValidationConstants.COMMENT_BODY_MAX,
            message = "コメントは280文字以内で入力してください")
        // 独自制約 @CodePointLength は springdoc が自動認識しないため、上限をここで明示する
        @Schema(description = "コメントの本文。280文字以内（絵文字は1文字と数える）", example = "参考になりました")
        String body) {}
