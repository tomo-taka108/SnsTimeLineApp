package com.example.snstimeline.file.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
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

  private final S3Client s3Client;
  private final String bucket;

  public S3FileStorageService(S3Client s3Client, @Value("${app.storage.s3.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
  }

  @Override
  public String store(byte[] content, String contentType) {
    String storageKey = StorageKeys.generate(contentType);
    s3Client.putObject(
        PutObjectRequest.builder().bucket(bucket).key(storageKey).contentType(contentType).build(),
        RequestBody.fromBytes(content));
    return storageKey;
  }

  @Override
  public byte[] load(String storageKey) {
    return s3Client
        .getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(storageKey).build())
        .asByteArray();
  }

  @Override
  public void delete(String storageKey) {
    // S3のDeleteObjectは存在しないキーでもエラーにならないため、冪等性はそのまま満たされる
    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
  }

  @Override
  public StorageType getStorageType() {
    return StorageType.S3;
  }
}
