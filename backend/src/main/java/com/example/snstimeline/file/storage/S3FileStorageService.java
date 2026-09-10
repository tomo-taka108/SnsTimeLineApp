package com.example.snstimeline.file.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3への保存（docs/07_architecture.md 3.3）。
 *
 * <p>{@code storage_key} は {@link LocalFileStorageService} と同じ形式をそのまま S3のオブジェクトキーとして使う。これにより既存ファイルを
 * S3へコピーして {@code storage_type} を更新するだけで移行できる。
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "S3")
public class S3FileStorageService implements FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

  private final S3Client s3Client;
  private final String bucket;

  public S3FileStorageService(S3Client s3Client, @Value("${app.storage.s3.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
  }

  @Override
  public String store(byte[] content, String contentType) {
    String storageKey = StorageKeys.generate(contentType);
    try {
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket)
              .key(storageKey)
              .contentType(contentType)
              .build(),
          RequestBody.fromBytes(content));
      return storageKey;
    } catch (SdkException e) {
      // 握りつぶさず、原因が分かるようログを出してから再throwする。
      // これが無いと「S3が落ちた」のか「アプリのバグ」なのか区別できず、
      // GlobalExceptionHandler の「予期しないエラー」に紛れてしまう
      // （docs/12_logging_and_operations.md 8章）。storageKeyは個人情報ではないため出してよい。
      log.error("S3操作に失敗 op=store bucket={} storageKey={}", bucket, storageKey, e);
      throw e;
    }
  }

  @Override
  public byte[] load(String storageKey) {
    try {
      return s3Client
          .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
          .asByteArray();
    } catch (SdkException e) {
      log.error("S3操作に失敗 op=load bucket={} storageKey={}", bucket, storageKey, e);
      throw e;
    }
  }

  @Override
  public void delete(String storageKey) {
    try {
      // S3のDeleteObjectは存在しないキーでもエラーにならないため、冪等性はそのまま満たされる
      s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
    } catch (SdkException e) {
      log.error("S3操作に失敗 op=delete bucket={} storageKey={}", bucket, storageKey, e);
      throw e;
    }
  }

  @Override
  public StorageType getStorageType() {
    return StorageType.S3;
  }
}
