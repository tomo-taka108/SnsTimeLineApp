package com.example.snstimeline.file.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3クライアントの生成（docs/10_infrastructure.md 4.5）。
 *
 * <p>{@code app.storage.type=S3} のときだけ Bean を作る。LOCAL で開発する分には
 * S3クライアントが生成されないため、AWSの認証情報が無くても起動できる。
 *
 * <p>認証情報はコードに書かない。{@link DefaultCredentialsProvider} が 「環境変数 → システムプロパティ → …… →
 * インスタンスプロファイル」の順に探すため、 ローカルでは環境変数（{@code AWS_ACCESS_KEY_ID} 等）、EC2ではIAMロールが そのまま使われる。
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "S3")
public class S3StorageConfig {

  @Bean
  public S3Client s3Client(@Value("${app.storage.s3.region}") String region) {
    return S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.create())
        .build();
  }
}
