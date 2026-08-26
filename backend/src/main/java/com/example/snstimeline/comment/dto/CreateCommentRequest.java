package com.example.snstimeline.comment.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
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
        String body) {}
