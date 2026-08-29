package com.example.snstimeline.post;

/**
 * 投稿の添付画像1件（docs/04_data_model.md 2.6）。
 *
 * <p>{@code post_images} と {@code stored_files} のJOIN結果をフラットに受ける（D-32）。 {@code
 * PostSummary.PostImageSummary} への組み立ては {@code PostService} が行う。
 */
public record PostImageRow(Long postId, Long fileId, Integer width, Integer height) {}
