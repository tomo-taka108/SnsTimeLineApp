package com.example.snstimeline.follow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * #21 / #22 フォロー・フォロー解除のレスポンス（docs/05_api_design.md #21, #22）。
 *
 * <p>{@code followerCount} は「フォローされた側（{@code userId}）」のフォロワー数 （docs/05_api_design.md #21
 * の注記）。{@code isFollowing} は {@code @JsonProperty} を明示する （record + boolean
 * アクセサの組み合わせがJacksonのバージョンによって {@code following} と解釈されうるため、 {@code LikeResponse} と同じ理由）。
 */
public record FollowResponse(@JsonProperty("isFollowing") boolean isFollowing, int followerCount) {}
