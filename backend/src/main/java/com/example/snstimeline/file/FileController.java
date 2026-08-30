package com.example.snstimeline.file;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.file.dto.UploadFileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 画像API（docs/05_api_design.md #25, #26）。 */
@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "画像", description = "画像のアップロードと配信")
public class FileController {

  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  /** #25 画像アップロード（F-IM-01, F-IM-03）。認証必要。 */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "画像アップロード",
      description =
          """
          画像をアップロードし、以降の投稿やプロフィール更新で使う `fileId` を返す。**認証が必要。**

          投稿に画像を付ける場合は、先にこのAPIでアップロードしてから、
          得られた `fileId` を投稿作成APIに渡す。
          """)
  @ApiResponse(responseCode = "201", description = "アップロード成功")
  @ApiResponse(responseCode = "413", description = "ファイルサイズが上限を超えている（FILE_TOO_LARGE）")
  @ApiResponse(responseCode = "415", description = "対応していないファイル形式（UNSUPPORTED_MEDIA_TYPE）")
  public UploadFileResponse upload(
      @AuthenticationPrincipal AuthPrincipal principal, @RequestParam("file") MultipartFile file) {
    return fileService.upload(principal.userId(), file);
  }

  /**
   * #26 画像配信（F-IM-02）。
   *
   * <p><b>認証不要。</b> {@code <img src>} は Authorization ヘッダを付けられないため。
   * 本アプリの投稿はすべて公開なので、画像だけを秘匿しても意味がない。
   *
   * <p>1年間のキャッシュを許可する。差し替えは新しい fileId になる設計のため、 同じURLの中身が変わることはない（docs/05_api_design.md #26）。
   */
  @GetMapping("/{fileId}")
  @SecurityRequirements
  @Operation(
      summary = "画像配信",
      description =
          """
          画像の実体を返す。**認証不要。**

          `<img src>` は Authorization ヘッダを付けられないため、認証を必須にできない。
          本アプリの投稿はすべて公開なので、画像だけを秘匿しても意味がない。

          差し替えは新しい `fileId` になる設計のため同じURLの中身は変わらず、1年間のキャッシュを許可している。
          """)
  @ApiResponse(responseCode = "200", description = "取得成功。画像のバイト列を返す")
  @ApiResponse(responseCode = "404", description = "ファイルが存在しない（NOT_FOUND）")
  public ResponseEntity<byte[]> download(@PathVariable Long fileId) {
    FileService.FileContent file = fileService.download(fileId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.contentType()))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .body(file.content());
  }
}
