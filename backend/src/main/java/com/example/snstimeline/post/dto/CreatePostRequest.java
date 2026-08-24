package com.example.snstimeline.post.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #6 POST /posts のリクエスト（docs/05_api_design.md #6）。
 *
 * <p>エラーメッセージは docs/03_screen_design.md MD-01 の文言をそのまま使う。
 *
 * <p>{@code imageFileIds} は今回のスコープ外（画像機能未実装）のため受け付けない。
 *
 * <p>min を {@code @CodePointLength} に併記しない。{@code @NotBlank} がトリム後の空文字を弾くため、min=1
 * を重ねると短い入力で2件エラーが返る （docs/05_api_design.md 8章「1フィールドに複数の制約を重ねすぎない」）。
 */
public record CreatePostRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "本文を入力してください")
        @CodePointLength(max = ValidationConstants.POST_BODY_MAX, message = "本文は280文字以内で入力してください")
        String body) {}
