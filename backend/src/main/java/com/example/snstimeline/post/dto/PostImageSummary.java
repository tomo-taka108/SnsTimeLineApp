package com.example.snstimeline.post.dto;

import com.example.snstimeline.file.dto.UploadFileResponse;
import com.example.snstimeline.post.PostImageRow;

/** 投稿に添付された画像（docs/05_api_design.md 4章 PostSummary.images）。 */
public record PostImageSummary(Long fileId, String url, Integer width, Integer height) {

  public static PostImageSummary from(PostImageRow row) {
    return new PostImageSummary(
        row.fileId(), UploadFileResponse.urlOf(row.fileId()), row.width(), row.height());
  }
}
