package com.example.snstimeline.file.storage;

/**
 * ファイルの実体を読み書きする窓口（docs/07_architecture.md 3章）。
 *
 * <p>呼び出し側（{@code FileService}）は保存先がローカルかS3かを知らない。 差し替えは {@code app.storage.type} の設定値だけで行う。
 *
 * <p>DBには絶対URLも物理パスも保存せず、{@code storage_key}（保存先内の相対パス）のみを持つ。 これが LOCAL → S3
 * の移行を設定変更だけで済ませられる理由（設計判断⑤）。
 */
public interface FileStorageService {

  /**
   * ファイルを保存し、{@code storage_key}（保存先内の相対パス）を返す。
   *
   * <p>キーは日付ディレクトリ + UUID で組み立てる（例: {@code 2026/08/29/uuid.jpg}）。
   * 元のファイル名はパスに使わない（パストラバーサル対策、docs/06_non_functional.md 3.5）。
   */
  String store(byte[] content, String contentType);

  /** {@code storage_key} のファイルを読み出す。 */
  byte[] load(String storageKey);

  /** {@code storage_key} のファイルを削除する。存在しない場合も例外にしない（冪等）。 */
  void delete(String storageKey);

  /** この実装が扱う保存先種別。{@code stored_files.storage_type} に保存する。 */
  StorageType getStorageType();
}
