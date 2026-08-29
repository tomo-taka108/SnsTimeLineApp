package com.example.snstimeline.file.dto;

/**
 * #25 画像アップロードのレスポンス（docs/05_api_design.md #25）。
 *
 * <p>{@code url} はDBに保存せず、都度組み立てる（設計判断⑤）。保存先が LOCAL から S3 に 変わってもクライアントから見えるURLは変わらない。
 */
public record UploadFileResponse(Long fileId, String url, Integer width, Integer height) {

  /** 配信URL（#26）。画像は {@code <img src>} から読むため、認証不要のこのパスを返す。 */
  public static String urlOf(Long fileId) {
    return "/api/v1/files/" + fileId;
  }
}
