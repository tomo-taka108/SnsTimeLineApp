package com.example.snstimeline.post.dto;

import com.example.snstimeline.common.CodePointLength;
import com.example.snstimeline.common.TrimDeserializer;
import com.example.snstimeline.common.ValidationConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * #6 POST /posts のリクエスト（docs/05_api_design.md #6）。
 *
 * <p>エラーメッセージは docs/03_screen_design.md MD-01 の文言をそのまま使う。
 *
 * <p>{@code imageFileIds} は自分がアップロードしたファイルのIDのみ指定できる。所有者チェックは {@code PostService} が {@code
 * FileService.assertOwnedBy} 経由で行う（D-44）。未送信なら空配列として扱う。
 *
 * <p>min を {@code @CodePointLength} に併記しない。{@code @NotBlank} がトリム後の空文字を弾くため、min=1
 * を重ねると短い入力で2件エラーが返る （docs/05_api_design.md 8章「1フィールドに複数の制約を重ねすぎない」）。
 */
public record CreatePostRequest(
    @JsonDeserialize(using = TrimDeserializer.class)
        @NotBlank(message = "本文を入力してください")
        @CodePointLength(max = ValidationConstants.POST_BODY_MAX, message = "本文は280文字以内で入力してください")
        String body,
    @Size(max = ValidationConstants.POST_IMAGE_COUNT_MAX, message = "画像は1枚まで添付できます")
        List<Long> imageFileIds) {

  /** {@code imageFileIds} が未送信（{@code null}）でも呼び出し側が空リストとして扱えるようにする。 */
  public List<Long> imageFileIdsOrEmpty() {
    return imageFileIds == null ? List.of() : imageFileIds;
  }
}
