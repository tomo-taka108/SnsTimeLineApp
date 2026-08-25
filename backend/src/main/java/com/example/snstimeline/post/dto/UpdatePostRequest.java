package com.example.snstimeline.post.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #8 PATCH /posts/{postId} のリクエスト（docs/05_api_design.md #8）。
 *
 * <p>投稿編集（F-PO-04）はdocs上はPhase2だが、今回MVPへ前倒しした （docs/09_decision_log.md D-30）。
 *
 * <p>{@code CreatePostRequest} と分けているのは、画像機能実装後に 作成側だけ {@code imageFileIds}
 * を持つようになるため（編集では画像を変更できない）。
 */
public record UpdatePostRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "本文を入力してください")
        @CodePointLength(max = ValidationConstants.POST_BODY_MAX, message = "本文は280文字以内で入力してください")
        String body) {}
