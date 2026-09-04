package com.example.snstimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.snstimeline.auth.dto.AuthResponse;
import com.example.snstimeline.auth.dto.LoginRequest;
import com.example.snstimeline.auth.dto.SignupRequest;
import com.example.snstimeline.auth.dto.TokenResponse;
import com.example.snstimeline.common.ConflictException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.common.UnauthorizedException;
import com.example.snstimeline.user.User;
import com.example.snstimeline.user.UserMapper;
import com.example.snstimeline.user.dto.UserSummary;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link AuthService} の単体テスト（docs/11_test_design.md 14章、ケース #161〜#176）。
 *
 * <p>中心は2つ。
 *
 * <ul>
 *   <li>ログイン失敗を<b>区別しない</b>設計（メール不存在とパスワード違いが同じ401、06 の 3.1）
 *   <li>パスワードが<b>平文のままDBに渡らない</b>ことの確認（CLAUDE.md 6章）
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final long USER_ID = 5L;

  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private RefreshTokenService refreshTokenService;

  @InjectMocks private AuthService authService;

  private static User user(long id, String passwordHash) {
    return new User(
        id,
        "user@example.com",
        passwordHash,
        "user1",
        "表示名1",
        null,
        null,
        null,
        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        null,
        null);
  }

  private void givenTokensIssued() {
    when(jwtTokenProvider.createAccessToken(USER_ID)).thenReturn("access-token");
    when(jwtTokenProvider.getAccessTokenExpiresInSeconds()).thenReturn(900L);
    when(refreshTokenService.issueNewFamily(USER_ID)).thenReturn("refresh-token");
  }

  @Nested
  @DisplayName("ログイン")
  class Login {

    @Test
    @DisplayName("#161 メールアドレスが存在しなければ401。passwordEncoder.matchesを呼ばない")
    void メールが存在しない() {
      when(userMapper.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> authService.login(new LoginRequest("nobody@example.com", "Password1")))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

      verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("#162 パスワードが違えば401。passwordEncoder.matchesは(生パスワード,ハッシュ)の順で呼ばれる")
    void パスワードが違う() {
      User existing = user(USER_ID, "hashed-password");
      when(userMapper.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
      when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

      assertThatThrownBy(
              () -> authService.login(new LoginRequest("user@example.com", "wrong-password")))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

      verify(passwordEncoder).matches("wrong-password", "hashed-password");
    }

    @Test
    @DisplayName("#163 メール・パスワードが正しければトークンを発行する")
    void ログイン成功() {
      User existing = user(USER_ID, "hashed-password");
      when(userMapper.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
      when(passwordEncoder.matches("Password1", "hashed-password")).thenReturn(true);
      givenTokensIssued();

      AuthResponse response = authService.login(new LoginRequest("user@example.com", "Password1"));

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isEqualTo("refresh-token");
      assertThat(response.user().id()).isEqualTo(USER_ID);
    }
  }

  @Nested
  @DisplayName("新規登録 — 重複判定の優先順位")
  class SignupDuplication {

    private SignupRequest request() {
      return new SignupRequest("new@example.com", "newuser", "新規ユーザー", "Password1");
    }

    @Test
    @DisplayName("#164 メール・ユーザー名とも既存ならメールが優先される（409 EMAIL_ALREADY_EXISTS）")
    void メールが優先される() {
      when(userMapper.existsByEmailIncludingDeleted("new@example.com")).thenReturn(true);

      assertThatThrownBy(() -> authService.signup(request()))
          .isInstanceOf(ConflictException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

      verify(userMapper, never()).existsByUsernameIncludingDeleted(anyString());
    }

    @Test
    @DisplayName("#165 メールのみ既存なら409 EMAIL_ALREADY_EXISTS")
    void メールのみ既存() {
      when(userMapper.existsByEmailIncludingDeleted("new@example.com")).thenReturn(true);

      assertThatThrownBy(() -> authService.signup(request()))
          .isInstanceOf(ConflictException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("#166 ユーザー名のみ既存なら409 USERNAME_ALREADY_EXISTS")
    void ユーザー名のみ既存() {
      when(userMapper.existsByEmailIncludingDeleted("new@example.com")).thenReturn(false);
      when(userMapper.existsByUsernameIncludingDeleted("newuser")).thenReturn(true);

      assertThatThrownBy(() -> authService.signup(request()))
          .isInstanceOf(ConflictException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("#167 TOCTOU: 事前チェック通過後にDuplicateKeyException(uq_users_username)なら409 USERNAME")
    void 同時登録の競合でユーザー名重複() {
      when(userMapper.existsByEmailIncludingDeleted("new@example.com")).thenReturn(false);
      when(userMapper.existsByUsernameIncludingDeleted("newuser")).thenReturn(false);
      when(passwordEncoder.encode("Password1")).thenReturn("hashed");
      when(userMapper.insert(org.mockito.ArgumentMatchers.any(User.class)))
          .thenThrow(
              new DuplicateKeyException(
                  "ERROR: duplicate key value violates unique constraint \"uq_users_username\""));

      assertThatThrownBy(() -> authService.signup(request()))
          .isInstanceOf(ConflictException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("#168 TOCTOU: 制約名が不明ならフォールバックで409 EMAIL")
    void 同時登録の競合で判別不能ならメールにフォールバック() {
      when(userMapper.existsByEmailIncludingDeleted("new@example.com")).thenReturn(false);
      when(userMapper.existsByUsernameIncludingDeleted("newuser")).thenReturn(false);
      when(passwordEncoder.encode("Password1")).thenReturn("hashed");
      when(userMapper.insert(org.mockito.ArgumentMatchers.any(User.class)))
          .thenThrow(
              new DuplicateKeyException(
                  "ERROR: duplicate key value violates unique constraint \"uq_users_email\""));

      assertThatThrownBy(() -> authService.signup(request()))
          .isInstanceOf(ConflictException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
  }

  @Nested
  @DisplayName("新規登録 — 正常系")
  class SignupSuccess {

    /**
     * #169 <b>CLAUDE.md 6章の必須方針そのもの。</b> 平文パスワードが {@code insert} に渡らないことを {@code ArgumentCaptor}
     * で確認する。渡る値は {@code passwordEncoder.encode} のスタブ戻り値と一致し、 リクエストの生パスワードとは一致しない。
     */
    @Test
    @DisplayName("#169 insertに渡るpasswordHashは平文ではなくencodeの戻り値")
    void パスワードはハッシュ化されて渡る() {
      when(userMapper.existsByEmailIncludingDeleted(anyString())).thenReturn(false);
      when(userMapper.existsByUsernameIncludingDeleted(anyString())).thenReturn(false);
      when(passwordEncoder.encode("Password1")).thenReturn("bcrypt-hashed-value");
      when(userMapper.insert(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(USER_ID);
      givenTokensIssued();

      authService.signup(new SignupRequest("new@example.com", "newuser", "新規ユーザー", "Password1"));

      ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
      verify(userMapper).insert(captor.capture());
      assertThat(captor.getValue().passwordHash()).isEqualTo("bcrypt-hashed-value");
      assertThat(captor.getValue().passwordHash()).isNotEqualTo("Password1");
    }

    @Test
    @DisplayName("#170 登録成功時のUserSummaryはavatarUrl=null")
    void 新規ユーザーのアバターはnull() {
      when(userMapper.existsByEmailIncludingDeleted(anyString())).thenReturn(false);
      when(userMapper.existsByUsernameIncludingDeleted(anyString())).thenReturn(false);
      when(passwordEncoder.encode(anyString())).thenReturn("hashed");
      when(userMapper.insert(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(USER_ID);
      givenTokensIssued();

      AuthResponse response =
          authService.signup(
              new SignupRequest("new@example.com", "newuser", "新規ユーザー", "Password1"));

      assertThat(response.user().avatarUrl()).isNull();
      assertThat(response.user().id()).isEqualTo(USER_ID);
    }
  }

  @Nested
  @DisplayName("トークン再発行")
  class Refresh {

    /** #171 rotateが先に呼ばれ、その後でユーザー存在を確認する順序。 */
    @Test
    @DisplayName("#171 rotate → userMapper.findByIdの順で呼ばれる")
    void ローテーションが先() {
      when(refreshTokenService.rotate("old-token"))
          .thenReturn(new RefreshTokenService.RotationResult(USER_ID, "new-token"));
      when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "hash")));
      when(jwtTokenProvider.createAccessToken(USER_ID)).thenReturn("access-token");
      when(jwtTokenProvider.getAccessTokenExpiresInSeconds()).thenReturn(900L);

      authService.refresh("old-token");

      InOrder inOrder = org.mockito.Mockito.inOrder(refreshTokenService, userMapper);
      inOrder.verify(refreshTokenService).rotate("old-token");
      inOrder.verify(userMapper).findById(USER_ID);
    }

    /**
     * #172 <b>ローテーション完了後にユーザーが論理削除済みと分かるケース。</b> 古いトークンは既に消費されているため、
     * このリクエストが失敗しても再試行はできない（新しいトークンで再試行することになる設計）。
     */
    @Test
    @DisplayName("#172 ローテーション後にユーザーが論理削除済みなら401（404ではない）")
    void ローテーション後にユーザーが見つからない() {
      when(refreshTokenService.rotate("old-token"))
          .thenReturn(new RefreshTokenService.RotationResult(USER_ID, "new-token"));
      when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.refresh("old-token"))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

      verify(refreshTokenService).rotate("old-token");
    }

    @Test
    @DisplayName("#173 レスポンスのrefreshTokenはローテーション後の新しい値")
    void 新しいトークンを返す() {
      when(refreshTokenService.rotate("old-token"))
          .thenReturn(new RefreshTokenService.RotationResult(USER_ID, "brand-new-token"));
      when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "hash")));
      when(jwtTokenProvider.createAccessToken(USER_ID)).thenReturn("access-token");
      when(jwtTokenProvider.getAccessTokenExpiresInSeconds()).thenReturn(900L);

      TokenResponse response = authService.refresh("old-token");

      assertThat(response.refreshToken()).isEqualTo("brand-new-token");
      assertThat(response.refreshToken()).isNotEqualTo("old-token");
    }
  }

  @Nested
  @DisplayName("ログアウト・現在ユーザー")
  class LogoutAndMe {

    @Test
    @DisplayName("#174 logoutはrevokeAllのみ呼ぶ")
    void ログアウト() {
      authService.logout(USER_ID);

      verify(refreshTokenService).revokeAll(USER_ID);
      verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("#175 getMeで論理削除済みユーザーなら404（401ではない）")
    void 論理削除済みユーザーは404() {
      when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> authService.getMe(USER_ID)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getMeは正常ならUserSummaryを返す")
    void 現在ユーザーを返す() {
      when(userMapper.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID, "hash")));

      UserSummary summary = authService.getMe(USER_ID);

      assertThat(summary.id()).isEqualTo(USER_ID);
    }
  }
}
