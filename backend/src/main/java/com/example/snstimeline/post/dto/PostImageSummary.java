package com.example.snstimeline.post.dto;

/**
 * 投稿に添付された画像（docs/05_api_design.md 4章 PostSummary.images）。
 *
 * <p>画像機能（F-IM-01〜03）は今回のスコープ外のため、現時点でこの型のインスタンスが 作られることは無い。{@code PostSummary.images}
 * は常に空配列を返す。型だけ先に用意しておく ことで、画像機能の実装時にAPIレスポンスの形が変わらずに済む。
 */
public record PostImageSummary(Long fileId, String url, Integer width, Integer height) {}
