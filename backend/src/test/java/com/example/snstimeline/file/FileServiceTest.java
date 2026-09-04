package com.example.snstimeline.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.file.dto.UploadFileResponse;
import com.example.snstimeline.file.storage.FileStorageService;
import com.example.snstimeline.file.storage.StorageType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * {@link FileService} の単体テスト（docs/11_test_design.md 16章、ケース #188〜#203）。
 *
 * <p><b>{@code @InjectMocks} は使わない。</b> コンストラクタが {@code int maxSizeMb} を取り、 Mockitoが自動注入すると {@code
 * 0} が入り、あらゆるアップロードが413で失敗してしまう。手動で {@code new} する。
 *
 * <p>マジックバイトは手で組む（バイナリをリポジトリに置かない）。
 *
 * <ul>
 *   <li>JPEG: {@code FF D8 FF}
 *   <li>PNG: {@code 89 50 4E 47 0D 0A 1A 0A}
 *   <li>WebP: {@code 52 49 46 46} + 8バイト目から {@code 57 45 42 50}
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

  private static final long ME_ID = 5L;
  private static final long MAX_SIZE_MB = 5;
  private static final long MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024;

  private static final byte[] PNG_BYTES = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00
  };
  private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x00};

  @Mock private FileMapper fileMapper;
  @Mock private FileStorageService storageService;

  private FileService fileService;

  @BeforeEach
  void setUp() {
    fileService = new FileService(fileMapper, storageService, (int) MAX_SIZE_MB);
  }

  private static byte[] webpBytes(boolean validTag) {
    byte[] bytes = new byte[12];
    bytes[0] = 0x52;
    bytes[1] = 0x49;
    bytes[2] = 0x46;
    bytes[3] = 0x46;
    // 4〜7バイト目はファイルサイズ格納領域（値は問わない）
    bytes[4] = 0x00;
    bytes[5] = 0x00;
    bytes[6] = 0x00;
    bytes[7] = 0x00;
    if (validTag) {
      bytes[8] = 0x57; // W
      bytes[9] = 0x45; // E
      bytes[10] = 0x42; // B
      bytes[11] = 0x50; // P
    } else {
      bytes[8] = 0x00;
      bytes[9] = 0x00;
      bytes[10] = 0x00;
      bytes[11] = 0x00;
    }
    return bytes;
  }

  @Nested
  @DisplayName("アップロード — サイズ")
  class UploadSize {

    @Test
    @DisplayName("#188 ちょうど上限（5MB）は通る（>であり>=ではない）")
    void 上限ちょうどは通る() {
      byte[] content = new byte[(int) MAX_SIZE_BYTES];
      System.arraycopy(PNG_BYTES, 0, content, 0, PNG_BYTES.length);
      MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", content);
      when(storageService.store(any(), anyString())).thenReturn("key");
      when(storageService.getStorageType()).thenReturn(StorageType.LOCAL);
      when(fileMapper.insert(any())).thenReturn(1L);

      UploadFileResponse response = fileService.upload(ME_ID, file);

      assertThat(response.fileId()).isEqualTo(1L);
    }

    /** #189 17項目「アップロードサイズ超過」。サイズ超過時はfile.getBytes()自体を呼ばない（メモリを消費しない）。 */
    @Test
    @DisplayName("#189 5MB超過なら413。file.getBytes()を呼ばない")
    void 上限超過は413() throws Exception {
      org.springframework.web.multipart.MultipartFile file =
          spy(new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES));
      when(file.getSize()).thenReturn(MAX_SIZE_BYTES + 1);

      assertThatThrownBy(() -> fileService.upload(ME_ID, file))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.FILE_TOO_LARGE);

      verify(file, never()).getBytes();
    }
  }

  @Nested
  @DisplayName("アップロード — 基本バリデーション")
  class UploadBasicValidation {

    @Test
    @DisplayName("#190 ファイルがnullまたは空なら400")
    void 空ファイルは400() {
      MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

      assertThatThrownBy(() -> fileService.upload(ME_ID, empty))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#191 未対応のContent-Typeなら415。getBytesに到達しない")
    void 未対応形式は415() throws Exception {
      org.springframework.web.multipart.MultipartFile file =
          spy(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes()));

      assertThatThrownBy(() -> fileService.upload(ME_ID, file))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

      verify(file, never()).getBytes();
    }
  }

  @Nested
  @DisplayName("アップロード — マジックバイト検証")
  class UploadMagicBytes {

    /** #192 17項目「画像のマジックバイト検証」。Content-Typeを偽装しても実体で弾かれる。storageServiceに触れない。 */
    @Test
    @DisplayName("#192 Content-Type=image/pngだが実体がJPEGなら415。storageに保存されない")
    void 偽装ファイルは拒否される() {
      MockMultipartFile spoofed =
          new MockMultipartFile("file", "fake.png", "image/png", JPEG_BYTES);

      assertThatThrownBy(() -> fileService.upload(ME_ID, spoofed))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);

      verify(storageService, never()).store(any(), anyString());
    }

    @Test
    @DisplayName("#193 正しいPNGは通る")
    void 正しいPNGは通る() {
      MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);
      when(storageService.store(any(), anyString())).thenReturn("key");
      when(storageService.getStorageType()).thenReturn(StorageType.LOCAL);
      when(fileMapper.insert(any())).thenReturn(1L);

      UploadFileResponse response = fileService.upload(ME_ID, file);

      assertThat(response.fileId()).isEqualTo(1L);
    }

    /** #194 WebPのファイルサイズ格納領域（4〜7バイト目）をスキップして8バイト目からWEBPタグを見る仕様の回帰。 */
    @Test
    @DisplayName("#194 RIFFヘッダは正しいが8バイト目からWEBPでなければ415")
    void WebPタグ不一致は拒否される() {
      MockMultipartFile file =
          new MockMultipartFile("file", "a.webp", "image/webp", webpBytes(false));

      assertThatThrownBy(() -> fileService.upload(ME_ID, file))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    @DisplayName("#195 正しいWebPは通る")
    void 正しいWebPは通る() {
      MockMultipartFile file =
          new MockMultipartFile("file", "a.webp", "image/webp", webpBytes(true));
      when(storageService.store(any(), anyString())).thenReturn("key");
      when(storageService.getStorageType()).thenReturn(StorageType.LOCAL);
      when(fileMapper.insert(any())).thenReturn(1L);

      UploadFileResponse response = fileService.upload(ME_ID, file);

      assertThat(response.fileId()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("アップロード — 保存内容")
  class UploadPersistence {

    @Test
    @DisplayName("#196 保存されるContent-Typeはenumの正規値（リクエストヘッダの生値ではない）")
    void 保存されるContentTypeは正規値() {
      MockMultipartFile file =
          new MockMultipartFile("file", "a.png", "Image/PNG", PNG_BYTES); // 表記ゆれ
      when(storageService.store(any(), anyString())).thenReturn("key");
      when(storageService.getStorageType()).thenReturn(StorageType.LOCAL);
      when(fileMapper.insert(any())).thenReturn(1L);

      fileService.upload(ME_ID, file);

      ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
      verify(storageService).store(any(), contentTypeCaptor.capture());
      assertThat(contentTypeCaptor.getValue()).isEqualTo("image/png");
    }

    /** #197 ImageIOが読めない最小バイト列でも例外にせず、width/height=nullで保存を続行する（WebPで実際に起きる）。 */
    @Test
    @DisplayName("#197 ImageIOが読めない実体でもwidth/height=nullで保存される")
    void 読めない画像でも保存は続く() {
      MockMultipartFile file =
          new MockMultipartFile("file", "a.webp", "image/webp", webpBytes(true));
      when(storageService.store(any(), anyString())).thenReturn("key");
      when(storageService.getStorageType()).thenReturn(StorageType.LOCAL);
      when(fileMapper.insert(any())).thenReturn(1L);

      UploadFileResponse response = fileService.upload(ME_ID, file);

      assertThat(response.width()).isNull();
      assertThat(response.height()).isNull();
    }

    @Test
    @DisplayName("#198 uploadedByは呼び出し元のmeIdと一致する")
    void アップロード者が正しく記録される() {
      MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES);
      when(storageService.store(any(), anyString())).thenReturn("key");
      when(storageService.getStorageType()).thenReturn(StorageType.LOCAL);
      when(fileMapper.insert(any())).thenReturn(1L);

      fileService.upload(ME_ID, file);

      ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
      verify(fileMapper).insert(captor.capture());
      assertThat(captor.getValue().uploadedBy()).isEqualTo(ME_ID);
    }
  }

  @Nested
  @DisplayName("配信")
  class Download {

    @Test
    @DisplayName("#199 存在しないfileIdなら404")
    void 存在しないファイルは404() {
      when(fileMapper.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> fileService.download(999L)).isInstanceOf(NotFoundException.class);
    }

    /** #200 画像配信は認証不要。所有者以外が呼んでも成功する（意図的に無検査）。 */
    @Test
    @DisplayName("#200 所有者以外が呼んでも配信される（所有者チェックをしない）")
    void 所有者以外でも配信される() {
      StoredFile file =
          new StoredFile(
              1L, "LOCAL", "key", "a.png", "image/png", 100L, 10, 10, 999L, OffsetDateTime.now());
      when(fileMapper.findById(1L)).thenReturn(Optional.of(file));
      when(storageService.load("key")).thenReturn(new byte[] {1, 2, 3});

      FileService.FileContent content = fileService.download(1L);

      assertThat(content.contentType()).isEqualTo("image/png");
    }
  }

  @Nested
  @DisplayName("所有者チェック（assertOwnedBy）")
  class AssertOwnedBy {

    /** #201 D-14の順序。存在しないfileIdは404（403ではない）。所有者の比較まで到達しない。 */
    @Test
    @DisplayName("#201 存在しないfileIdなら404（403ではない）")
    void 存在しないファイルは404を返す() {
      when(fileMapper.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> fileService.assertOwnedBy(ME_ID, 999L))
          .isInstanceOf(NotFoundException.class);
    }

    /** #202 17項目「ファイル所有者チェック」の実体。 */
    @Test
    @DisplayName("#202 他人のfileIdなら403")
    void 他人のファイルは403() {
      StoredFile file =
          new StoredFile(
              1L, "LOCAL", "key", "a.png", "image/png", 100L, 10, 10, 999L, OffsetDateTime.now());
      when(fileMapper.findById(1L)).thenReturn(Optional.of(file));

      assertThatThrownBy(() -> fileService.assertOwnedBy(ME_ID, 1L))
          .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("#203 自分のfileIdなら例外を投げない")
    void 自分のファイルは通る() {
      StoredFile file =
          new StoredFile(
              1L, "LOCAL", "key", "a.png", "image/png", 100L, 10, 10, ME_ID, OffsetDateTime.now());
      when(fileMapper.findById(1L)).thenReturn(Optional.of(file));

      fileService.assertOwnedBy(ME_ID, 1L);
    }
  }
}
