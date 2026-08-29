package com.example.snstimeline.file.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * ローカルディレクトリへの保存（docs/07_architecture.md 3章）。
 *
 * <p>AWSアカウントが無くても開発できる状態を保つための既定実装 （docs/10_infrastructure.md 5章）。{@code app.storage.type=LOCAL}
 * のときだけ有効になる。
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

  private final Path root;

  public LocalFileStorageService(@Value("${app.storage.local-path}") String localPath) {
    this.root = Path.of(localPath).toAbsolutePath().normalize();
  }

  @Override
  public String store(byte[] content, String contentType) {
    String storageKey = StorageKeys.generate(contentType);
    Path destination = resolve(storageKey);
    try {
      Files.createDirectories(destination.getParent());
      Files.write(destination, content);
    } catch (IOException e) {
      throw new UncheckedIOException("ファイルの保存に失敗しました", e);
    }
    return storageKey;
  }

  @Override
  public byte[] load(String storageKey) {
    try {
      return Files.readAllBytes(resolve(storageKey));
    } catch (IOException e) {
      throw new UncheckedIOException("ファイルの読み出しに失敗しました", e);
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      Files.deleteIfExists(resolve(storageKey));
    } catch (IOException e) {
      throw new UncheckedIOException("ファイルの削除に失敗しました", e);
    }
  }

  @Override
  public StorageType getStorageType() {
    return StorageType.LOCAL;
  }

  /**
   * storage_key を保存先の絶対パスに変換する。
   *
   * <p>キーは自前で採番している（{@link StorageKeys}）が、DBから読んだ値をそのまま連結すると 「{@code ../}
   * を含む値が入り込んだ場合に保存先の外を指す」経路が残る。ここで正規化して 保存先配下であることを必ず確認する（docs/06_non_functional.md 3.5）。
   */
  private Path resolve(String storageKey) {
    Path resolved = root.resolve(storageKey).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("保存先の外を指すキーです");
    }
    return resolved;
  }
}
