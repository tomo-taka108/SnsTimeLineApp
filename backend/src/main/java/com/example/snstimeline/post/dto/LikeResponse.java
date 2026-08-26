package com.example.snstimeline.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * #14 / #15 いいね・いいね解除のレスポンス（docs/05_api_design.md #14, #15）。
 *
 * <p>{@code isLikedByMe} は {@code @JsonProperty} を明示する。record + boolean アクセサの組み合わせは
 * Jacksonのバージョンによって {@code likedByMe} と解釈されうるため、確実にAPI契約どおりのキー名で出す （{@code PostSummary} と同じ理由）。
 */
public record LikeResponse(int likeCount, @JsonProperty("isLikedByMe") boolean isLikedByMe) {}
