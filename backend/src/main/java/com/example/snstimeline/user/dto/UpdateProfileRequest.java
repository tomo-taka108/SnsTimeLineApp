package com.example.snstimeline.user.dto;

import com.example.snstimeline.common.ValidationConstants;
import tools.jackson.databind.JsonNode;

/**
 * #19 PATCH /users/me のリクエスト（docs/05_api_design.md #19）。送られたフィールドのみ更新する。
 *
 * <p><b>{@code record} ではなく生の {@link JsonNode} で受ける理由</b>: 「{@code bio} フィールド自体を送らない（変更しない）」と
 * 「{@code null} を明示的に送る（自己紹介を削除する）」を区別する必要がある （docs/05_api_design.md #19「{@code bio}:
 * 160文字以内。{@code null} を明示的に送れば削除」）。
 *
 * <p>検証してわかったこと: Jackson は {@code Optional<String>} フィールドに対して「未送信」と 「明示的な {@code null}」の両方を {@code
 * Optional.empty()} に潰してしまい、区別できない （{@code JsonMapper} でのデシリアライズを実際に試して確認済み）。区別するには {@link
 * JsonNode#has(String)}（キーの有無）で見るしかないため、record を諦めて {@code JsonNode} を直接受ける。
 *
 * <p>バリデーションを {@code @Valid} ではなく {@code UserService} 側の手動チェックで行うのも同じ理由による （{@code JsonNode} には
 * Bean Validation を適用できない）。
 *
 * <p>{@code email} と {@code username} は変更できない仕様のため、このDTOでは読み取らない。送られても無視される。
 *
 * <p>{@code avatarFileId} は {@code bio} と同じ「未送信＝変更しない／{@code null}＝削除」の3点セットで扱う
 * （docs/05_api_design.md #19「{@code avatarFileId}: null で削除」）。自分がアップロードしたファイルかどうかの 所有者チェックは {@code
 * UserService} が {@code FileService.assertOwnedBy} 経由で行う（D-44）。
 */
public record UpdateProfileRequest(JsonNode body) {

  /** displayName フィールドが送られているか。 */
  public boolean hasDisplayName() {
    return body.has("displayName") && !body.get("displayName").isNull();
  }

  /**
   * トリム済みの displayName。{@link #hasDisplayName()} が false なら呼ばないこと。
   *
   * <p>{@code TrimDeserializer} は record 経由のデシリアライズにしか効かないため、ここで手動トリムする （docs/05_api_design.md
   * 8章「文字列は前後の空白をトリムしてから検証する」）。
   */
  public String displayName() {
    return body.get("displayName").asString().strip();
  }

  public boolean isDisplayNameBlank() {
    return hasDisplayName() && displayName().isBlank();
  }

  public boolean isDisplayNameTooLong() {
    return hasDisplayName()
        && displayName().codePointCount(0, displayName().length())
            > ValidationConstants.DISPLAY_NAME_MAX;
  }

  /** bio フィールドがリクエストに含まれているか（値が null でも true。「削除」の指示として扱うため）。 */
  public boolean hasBio() {
    return body.has("bio");
  }

  /** bio が明示的に null で送られたか（＝自己紹介を削除する指示）。 */
  public boolean isBioNull() {
    return hasBio() && body.get("bio").isNull();
  }

  /** トリム済みの bio。{@link #hasBio()} が true かつ {@link #isBioNull()} が false のときだけ呼ぶこと。 */
  public String bio() {
    return body.get("bio").asString().strip();
  }

  public boolean isBioTooLong() {
    return hasBio()
        && !isBioNull()
        && bio().codePointCount(0, bio().length()) > ValidationConstants.BIO_MAX;
  }

  /** avatarFileId フィールドがリクエストに含まれているか（値が null でも true。「削除」の指示として扱うため）。 */
  public boolean hasAvatarFileId() {
    return body.has("avatarFileId");
  }

  /** avatarFileId が明示的に null で送られたか（＝プロフィール画像を削除する指示）。 */
  public boolean isAvatarFileIdNull() {
    return hasAvatarFileId() && body.get("avatarFileId").isNull();
  }

  /** avatarFileId が数値以外で送られていないか。{@link #hasAvatarFileId()} が true かつ削除でないときだけ意味を持つ。 */
  public boolean isAvatarFileIdInvalid() {
    return hasAvatarFileId() && !isAvatarFileIdNull() && !body.get("avatarFileId").isNumber();
  }

  /**
   * avatarFileId。{@link #hasAvatarFileId()} が true かつ {@link #isAvatarFileIdNull()} が false
   * のときだけ呼ぶこと。
   */
  public Long avatarFileId() {
    return body.get("avatarFileId").asLong();
  }
}
