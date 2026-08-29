package com.example.snstimeline.file;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.file.dto.UploadFileResponse;
import com.example.snstimeline.file.storage.FileStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 画像アップロード・配信の業務ロジック（docs/05_api_design.md #25, #26）。 */
@Service
public class FileService {

  private final FileMapper fileMapper;
  private final FileStorageService storageService;
  private final long maxSizeBytes;

  public FileService(
      FileMapper fileMapper,
      FileStorageService storageService,
      @Value("${app.upload.max-size-mb}") int maxSizeMb) {
    this.fileMapper = fileMapper;
    this.storageService = storageService;
    this.maxSizeBytes = (long) maxSizeMb * 1024 * 1024;
  }

  /**
   * #25 画像アップロード（F-IM-01, F-IM-03）。
   *
   * <p>検証の順序は「サイズ → Content-Type → 実体の先頭バイト」。 実体を読む前にサイズで弾くことで、巨大なファイルをメモリに載せない。
   */
  @Transactional
  public UploadFileResponse upload(Long meId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    if (file.getSize() > maxSizeBytes) {
      throw new ApiException(ErrorCode.FILE_TOO_LARGE);
    }

    ImageType imageType = ImageType.fromContentType(file.getContentType());
    if (imageType == null) {
      throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    byte[] content = readAll(file);
    // Content-Type は送信側が自由に名乗れるため、実体と一致するかを必ず確かめる
    // （docs/06_non_functional.md 3.5）
    if (!imageType.matches(content)) {
      throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    String storageKey = storageService.store(content, imageType.contentType());
    Dimension dimension = readDimension(content);

    StoredFile stored =
        new StoredFile(
            null,
            storageService.getStorageType().name(),
            storageKey,
            file.getOriginalFilename(),
            imageType.contentType(),
            (long) content.length,
            dimension.width(),
            dimension.height(),
            meId,
            null);
    Long fileId = fileMapper.insert(stored);
    return new UploadFileResponse(
        fileId, UploadFileResponse.urlOf(fileId), stored.width(), stored.height());
  }

  /** #26 画像配信（F-IM-02）。認証不要のため、呼び出し元のユーザーは問わない。 */
  @Transactional(readOnly = true)
  public FileContent download(Long fileId) {
    StoredFile file = fileMapper.findById(fileId).orElseThrow(NotFoundException::new);
    return new FileContent(storageService.load(file.storageKey()), file.contentType());
  }

  /**
   * 投稿への添付（#6）・プロフィール画像の設定（#19）で使う所有者チェック。
   *
   * <p>{@code stored_files} の知識をファイルモジュールの外に出さないため、 {@code PostService} / {@code UserService} は
   * {@link FileMapper} を直接使わず、必ずこのメソッドを経由する （docs/09_decision_log.md D-44）。
   *
   * <p>認可は「① 存在チェック→404 → ② 所有者チェック→403」の順を守る（D-14）。 存在しない {@code fileId} は #6 の設計書どおり404、#19
   * も同じ順序に揃える（D-43）。
   */
  @Transactional(readOnly = true)
  public void assertOwnedBy(Long meId, Long fileId) {
    StoredFile file = fileMapper.findById(fileId).orElseThrow(NotFoundException::new);
    if (!file.uploadedBy().equals(meId)) {
      throw new ForbiddenException();
    }
  }

  /** 配信用のファイル実体とContent-Type。 */
  public record FileContent(byte[] content, String contentType) {}

  private static byte[] readAll(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("アップロードされたファイルを読み取れませんでした", e);
    }
  }

  /**
   * 画像の縦横を読む。レイアウトシフト防止のためであり、必須ではない （docs/04_data_model.md 2.7 の width / height は NULL 許容）。
   *
   * <p>ImageIO は WebP を標準では読めないため、読めなければ null を返して先へ進む。
   */
  private static Dimension readDimension(byte[] content) {
    try (var input = new ByteArrayInputStream(content)) {
      BufferedImage image = ImageIO.read(input);
      if (image == null) {
        return new Dimension(null, null);
      }
      return new Dimension(image.getWidth(), image.getHeight());
    } catch (IOException e) {
      return new Dimension(null, null);
    }
  }

  private record Dimension(Integer width, Integer height) {}
}
