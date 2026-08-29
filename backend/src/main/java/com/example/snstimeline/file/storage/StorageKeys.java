package com.example.snstimeline.file.storage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code storage_key} の組み立て（docs/07_architecture.md 3章）。
 *
 * <p>形式は {@code yyyy/MM/dd/uuid.ext}。日付で分けるのは1ディレクトリのファイル数を抑えるため、
 * UUIDにするのは元のファイル名をパスに混ぜないため（パストラバーサル対策、 docs/06_non_functional.md 3.5）。LOCAL / S3 のどちらでも同じキーを使う。
 */
public final class StorageKeys {

  private StorageKeys() {}

  public static String generate(String contentType) {
    LocalDate today = LocalDate.now();
    return "%04d/%02d/%02d/%s.%s"
        .formatted(
            today.getYear(),
            today.getMonthValue(),
            today.getDayOfMonth(),
            UUID.randomUUID(),
            extensionOf(contentType));
  }

  private static String extensionOf(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      default -> throw new IllegalArgumentException("対応していないContent-Typeです: " + contentType);
    };
  }
}
