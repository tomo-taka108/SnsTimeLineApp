package com.example.snstimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.file.FileService;
import com.example.snstimeline.follow.FollowMapper;
import com.example.snstimeline.user.dto.UpdateProfileRequest;
import com.example.snstimeline.user.dto.UserProfile;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link UserService} の単体テスト（docs/11_test_design.md 12章、ケース #121〜#138）。
 *
 * <p>本クラスの中心は2つ。
 *
 * <ul>
 *   <li>{@code postCount}/{@code followingCount}/{@code followerCount} が<b>取り違えられていないか</b> （すべて同じ
 *       {@code int} の戻り値のため、コンパイラは検出できない）
 *   <li>{@code updateProfile} の「未送信／null／値あり」という<b>3状態</b>の扱い
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final long ME_ID = 5L;
  private static final long OTHER_ID = 42L;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Mock private UserMapper userMapper;
  @Mock private FollowMapper followMapper;
  @Mock private FileService fileService;

  @InjectMocks private UserService userService;

  private static User user(long id) {
    return new User(
        id,
        "user" + id + "@example.com",
        "hash",
        "user" + id,
        "表示名" + id,
        "自己紹介",
        null,
        null,
        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        null,
        null);
  }

  private static UpdateProfileRequest request(String json) {
    return new UpdateProfileRequest(JSON.readTree(json));
  }

  @Nested
  @DisplayName("プロフィール取得")
  class GetProfile {

    /**
     * #121 <b>本節の核心。</b> 3つとも同じ {@code int} の戻り値のため、実装が呼び出しを取り違えても型エラーにならない。
     * 必ず異なる値をスタブし、レスポンスの各フィールドと1対1で対応することを確認する。
     */
    @Test
    @DisplayName("#121 postCount/followingCount/followerCountが取り違えられていない")
    void カウントが正しく対応する() {
      when(userMapper.findById(OTHER_ID)).thenReturn(Optional.of(user(OTHER_ID)));
      when(followMapper.exists(ME_ID, OTHER_ID)).thenReturn(false);
      when(userMapper.countPosts(OTHER_ID)).thenReturn(3);
      when(followMapper.countFollowing(OTHER_ID)).thenReturn(5);
      when(followMapper.countFollowers(OTHER_ID)).thenReturn(7);

      UserProfile profile = userService.getProfile(ME_ID, OTHER_ID);

      assertThat(profile.postCount()).isEqualTo(3);
      assertThat(profile.followingCount()).isEqualTo(5);
      assertThat(profile.followerCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("#122 対象ユーザーが存在しなければ404。カウント系メソッドを呼ばない")
    void 対象が存在しない() {
      when(userMapper.findById(OTHER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> userService.getProfile(ME_ID, OTHER_ID))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(followMapper);
    }

    /** #123 isMe のときは自分自身をフォローしているか判定する意味が無いため、exists を呼ばない設計。 */
    @Test
    @DisplayName("#123 isMeなら followMapper.exists を呼ばない。isFollowingは常にfalse")
    void 自分自身ならフォロー判定しない() {
      when(userMapper.findById(ME_ID)).thenReturn(Optional.of(user(ME_ID)));
      when(userMapper.countPosts(ME_ID)).thenReturn(0);
      when(followMapper.countFollowing(ME_ID)).thenReturn(0);
      when(followMapper.countFollowers(ME_ID)).thenReturn(0);

      UserProfile profile = userService.getProfile(ME_ID, ME_ID);

      verify(followMapper, never()).exists(ME_ID, ME_ID);
      assertThat(profile.isMe()).isTrue();
      assertThat(profile.isFollowing()).isFalse();
    }

    @Test
    @DisplayName("#124 他人のプロフィールでフォロー中ならisFollowing=true")
    void 他人をフォロー中() {
      when(userMapper.findById(OTHER_ID)).thenReturn(Optional.of(user(OTHER_ID)));
      when(followMapper.exists(ME_ID, OTHER_ID)).thenReturn(true);
      when(userMapper.countPosts(OTHER_ID)).thenReturn(0);
      when(followMapper.countFollowing(OTHER_ID)).thenReturn(0);
      when(followMapper.countFollowers(OTHER_ID)).thenReturn(0);

      UserProfile profile = userService.getProfile(ME_ID, OTHER_ID);

      verify(followMapper).exists(ME_ID, OTHER_ID);
      assertThat(profile.isFollowing()).isTrue();
    }
  }

  @Nested
  @DisplayName("プロフィール更新 — bioの3状態")
  class UpdateBio {

    private void givenSelfProfile() {
      when(userMapper.findById(ME_ID)).thenReturn(Optional.of(user(ME_ID)));
      when(followMapper.countFollowing(ME_ID)).thenReturn(0);
      when(followMapper.countFollowers(ME_ID)).thenReturn(0);
      when(userMapper.countPosts(ME_ID)).thenReturn(0);
    }

    @Test
    @DisplayName("#125 bio未送信ならbioProvided=falseでMapperに渡る")
    void bio未送信() {
      givenSelfProfile();
      when(userMapper.updateProfile(ME_ID, null, false, null, false, null, false, null))
          .thenReturn(1);

      userService.updateProfile(ME_ID, request("{}"));

      verify(userMapper).updateProfile(ME_ID, null, false, null, false, null, false, null);
    }

    @Test
    @DisplayName("#126 bioが明示的にnullならbioProvided=true、値はnull（削除）")
    void bio明示的にnull() {
      givenSelfProfile();
      when(userMapper.updateProfile(ME_ID, null, true, null, false, null, false, null))
          .thenReturn(1);

      userService.updateProfile(ME_ID, request("{\"bio\":null}"));

      verify(userMapper).updateProfile(ME_ID, null, true, null, false, null, false, null);
    }

    @Test
    @DisplayName("#127 bioに値があればトリムして渡す")
    void bio値あり() {
      givenSelfProfile();
      when(userMapper.updateProfile(ME_ID, null, true, "新しい自己紹介", false, null, false, null))
          .thenReturn(1);

      userService.updateProfile(ME_ID, request("{\"bio\":\"  新しい自己紹介  \"}"));

      verify(userMapper).updateProfile(ME_ID, null, true, "新しい自己紹介", false, null, false, null);
    }

    @Test
    @DisplayName("#128 bioが161コードポイントなら400")
    void bioが長すぎる() {
      String tooLong = "あ".repeat(161);
      assertThatThrownBy(
              () -> userService.updateProfile(ME_ID, request("{\"bio\":\"" + tooLong + "\"}")))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
  }

  @Nested
  @DisplayName("プロフィール更新 — ファイル所有者チェック")
  class UpdateFileOwnership {

    private void givenSelfProfile() {
      when(userMapper.findById(ME_ID)).thenReturn(Optional.of(user(ME_ID)));
      when(followMapper.countFollowing(ME_ID)).thenReturn(0);
      when(followMapper.countFollowers(ME_ID)).thenReturn(0);
      when(userMapper.countPosts(ME_ID)).thenReturn(0);
    }

    @Test
    @DisplayName("#129 avatarFileId未送信ならassertOwnedByを呼ばない")
    void avatar未送信() {
      givenSelfProfile();
      when(userMapper.updateProfile(ME_ID, null, false, null, false, null, false, null))
          .thenReturn(1);

      userService.updateProfile(ME_ID, request("{}"));

      verifyNoInteractions(fileService);
    }

    /** #130 null（削除）は自分のファイルかどうかを問わない仕様（UserService のコメント参照）。 */
    @Test
    @DisplayName("#130 avatarFileIdが明示的にnull（削除）ならassertOwnedByを呼ばない")
    void avatar削除は所有者チェックしない() {
      givenSelfProfile();
      when(userMapper.updateProfile(ME_ID, null, false, null, true, null, false, null))
          .thenReturn(1);

      userService.updateProfile(ME_ID, request("{\"avatarFileId\":null}"));

      verifyNoInteractions(fileService);
    }

    @Test
    @DisplayName("#131 avatarFileIdが数値以外なら400。fileServiceに触れない")
    void avatarが数値以外() {
      assertThatThrownBy(
              () -> userService.updateProfile(ME_ID, request("{\"avatarFileId\":\"abc\"}")))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);

      verifyNoInteractions(fileService);
    }

    /** #132 avatarが先にチェックされ403で止まるため、coverの所有者チェックには到達しない。 */
    @Test
    @DisplayName("#132 avatarとcover両方が他人のIDでも、avatar側の403で止まりcoverは検査されない")
    void avatarが先に検査される() {
      doThrowForbidden(10L);

      assertThatThrownBy(
              () ->
                  userService.updateProfile(
                      ME_ID, request("{\"avatarFileId\":10,\"coverFileId\":20}")))
          .isInstanceOf(ForbiddenException.class);

      verify(fileService).assertOwnedBy(ME_ID, 10L);
      verify(fileService, never()).assertOwnedBy(ME_ID, 20L);
    }

    /** #133 17項目「ファイル所有者チェック」そのもの。他人のファイルではプロフィールが更新されない。 */
    @Test
    @DisplayName("#133 他人のavatarFileIdなら403。userMapper.updateProfileに触れない")
    void 他人のファイルは拒否される() {
      doThrowForbidden(10L);

      assertThatThrownBy(() -> userService.updateProfile(ME_ID, request("{\"avatarFileId\":10}")))
          .isInstanceOf(ForbiddenException.class);

      verifyNoInteractions(userMapper);
    }

    /** #134 D-14の順序。存在しないfileIdは403ではなく404（FileService.assertOwnedByの責務）。 */
    @Test
    @DisplayName("#134 存在しないavatarFileIdなら404（403ではない）")
    void 存在しないファイルは404() {
      org.mockito.Mockito.doThrow(new NotFoundException())
          .when(fileService)
          .assertOwnedBy(ME_ID, 10L);

      assertThatThrownBy(() -> userService.updateProfile(ME_ID, request("{\"avatarFileId\":10}")))
          .isInstanceOf(NotFoundException.class);
    }

    private void doThrowForbidden(long fileId) {
      org.mockito.Mockito.doThrow(new ForbiddenException())
          .when(fileService)
          .assertOwnedBy(ME_ID, fileId);
    }
  }

  @Nested
  @DisplayName("プロフィール更新 — displayNameのバリデーション")
  class UpdateDisplayName {

    @Test
    @DisplayName("#135 displayNameが空白のみなら400")
    void 空白のみ() {
      assertThatThrownBy(
              () -> userService.updateProfile(ME_ID, request("{\"displayName\":\"   \"}")))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#136 displayNameが51文字なら400")
    void 長すぎる() {
      String tooLong = "あ".repeat(51);
      assertThatThrownBy(
              () ->
                  userService.updateProfile(
                      ME_ID, request("{\"displayName\":\"" + tooLong + "\"}")))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
  }

  @Nested
  @DisplayName("プロフィール更新 — 更新後の挙動")
  class UpdateResult {

    /** #137 更新成功時、レスポンスは自己呼び出しの getProfile(meId, meId) の結果そのもの。 */
    @Test
    @DisplayName("#137 更新成功後はgetProfile(meId, meId)の結果を返す")
    void 更新後は最新のプロフィールを返す() {
      when(userMapper.updateProfile(ME_ID, "新しい表示名", false, null, false, null, false, null))
          .thenReturn(1);
      when(userMapper.findById(ME_ID)).thenReturn(Optional.of(user(ME_ID)));
      when(userMapper.countPosts(ME_ID)).thenReturn(2);
      when(followMapper.countFollowing(ME_ID)).thenReturn(4);
      when(followMapper.countFollowers(ME_ID)).thenReturn(6);

      UserProfile profile =
          userService.updateProfile(ME_ID, request("{\"displayName\":\"新しい表示名\"}"));

      assertThat(profile.postCount()).isEqualTo(2);
      assertThat(profile.followingCount()).isEqualTo(4);
      assertThat(profile.followerCount()).isEqualTo(6);
      verify(followMapper, never()).exists(ME_ID, ME_ID);
    }

    /** #138 自分自身の操作でも、論理削除との競合を考慮してaffected==0を判定する。 */
    @Test
    @DisplayName("#138 affected==0なら404")
    void 競合時は404() {
      when(userMapper.updateProfile(ME_ID, "新しい表示名", false, null, false, null, false, null))
          .thenReturn(0);

      assertThatThrownBy(
              () -> userService.updateProfile(ME_ID, request("{\"displayName\":\"新しい表示名\"}")))
          .isInstanceOf(NotFoundException.class);
    }
  }
}
