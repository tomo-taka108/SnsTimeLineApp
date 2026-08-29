package com.example.snstimeline.file;

import java.util.Arrays;

/**
 * アップロードを許可する画像形式（docs/06_non_functional.md 3.5）。
 *
 * <p>Content-Type ヘッダは送信側が自由に名乗れるため、それだけでは検証にならない。 {@code .jpg} にリネームしただけの実行ファイルを弾くために、実体の先頭バイト
 * （マジックナンバー）が名乗った形式と一致することを必ず確認する。
 */
public enum ImageType {
  JPEG("image/jpeg", new int[] {0xFF, 0xD8, 0xFF}),
  PNG("image/png", new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
  // WebP は RIFF コンテナ。先頭4バイトが "RIFF"、8バイト目からが "WEBP" で、
  // 4〜7バイト目はファイルサイズのため値が定まらない。そこを飛ばして判定する
  WEBP("image/webp", new int[] {0x52, 0x49, 0x46, 0x46}, new int[] {0x57, 0x45, 0x42, 0x50});

  private final String contentType;
  private final int[] prefix;
  private final int[] webpTag;

  ImageType(String contentType, int[] prefix) {
    this(contentType, prefix, null);
  }

  ImageType(String contentType, int[] prefix, int[] webpTag) {
    this.contentType = contentType;
    this.prefix = prefix;
    this.webpTag = webpTag;
  }

  public String contentType() {
    return contentType;
  }

  /** Content-Type 名から探す。未対応の形式なら {@code null}。 */
  public static ImageType fromContentType(String contentType) {
    return Arrays.stream(values())
        .filter(type -> type.contentType.equalsIgnoreCase(contentType))
        .findFirst()
        .orElse(null);
  }

  /** 実体の先頭バイトがこの形式と一致するか。 */
  public boolean matches(byte[] content) {
    if (!startsWith(content, prefix, 0)) {
      return false;
    }
    return webpTag == null || startsWith(content, webpTag, 8);
  }

  private static boolean startsWith(byte[] content, int[] expected, int offset) {
    if (content.length < offset + expected.length) {
      return false;
    }
    for (int i = 0; i < expected.length; i++) {
      if ((content[offset + i] & 0xFF) != expected[i]) {
        return false;
      }
    }
    return true;
  }
}
