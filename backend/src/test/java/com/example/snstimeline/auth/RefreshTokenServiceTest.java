package com.example.snstimeline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.UnauthorizedException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RefreshTokenService} の単体テスト（docs/11_test_design.md 15章、ケース #177〜#186）。
 *
 * <p><b>{@code @InjectMocks} は使わない。</b> コンストラクタが {@code long expirationDays} を取り、 Mockitoが自動注入すると
 * {@code 0} が入ってしまう。手動で {@code new} する。
 *
 * <p>中心は盗用検知（15.1節）。使用済みトークンの再提示と、失効済み/期限切れの再提示は<b>どちらも401</b>だが、 {@code revoker}
 * が呼ばれるのは<b>使用済みの場合だけ</b>。この区別ができて初めて盗用検知のテストと言える。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final long USER_ID = 5L;
  private static final long EXPIRATION_DAYS = 14L;

  @Mock private RefreshTokenMapper refreshTokenMapper;
  @Mock private RefreshTokenRevoker revoker;

  private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    refreshTokenService = new RefreshTokenService(refreshTokenMapper, revoker, EXPIRATION_DAYS);
  }

  private static RefreshToken stored(
      UUID familyId, OffsetDateTime usedAt, OffsetDateTime revokedAt, OffsetDateTime expiresAt) {
    return new RefreshToken(
        1L, USER_ID, "stored-hash", familyId, expiresAt, usedAt, revokedAt, null);
  }

  @Nested
  @DisplayName("ローテーション")
  class Rotate {

    @Test
    @DisplayName("#177 トークンが見つからなければ401。revoker・markUsedに一切触れない")
    void トークンが見つからない() {
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> refreshTokenService.rotate("unknown-token"))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

      org.mockito.Mockito.verifyNoInteractions(revoker);
      verify(refreshTokenMapper, never()).markUsed(any(), any());
    }

    /**
     * #178 <b>本節の核心。</b> 使用済みトークンの再提示は盗用の疑いがあるため、 {@code revoker.revokeFamilyInNewTransaction}
     * が<b>先に</b>呼ばれ、その後で401を投げる（InOrder）。{@code markUsed} は呼ばれない。
     */
    @Test
    @DisplayName("#178 使用済みトークンの再提示: revokerを呼んでから401（markUsedは呼ばない）")
    void 使用済みトークンは盗用として全ファミリー失効() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken used = stored(familyId, OffsetDateTime.now().minusMinutes(1), null, future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(used));

      assertThatThrownBy(() -> refreshTokenService.rotate("reused-token"))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

      InOrder order = inOrder(revoker);
      order.verify(revoker).revokeFamilyInNewTransaction(familyId, USER_ID);
      verify(refreshTokenMapper, never()).markUsed(any(), any());
    }

    /** #179 失効済み（ログアウト等）は使用済みと区別する。revokerは呼ばない。 */
    @Test
    @DisplayName("#179 失効済みトークンは401だがrevokerは呼ばない")
    void 失効済みトークンはrevokerを呼ばない() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken revoked = stored(familyId, null, OffsetDateTime.now().minusMinutes(1), future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

      assertThatThrownBy(() -> refreshTokenService.rotate("revoked-token"))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

      org.mockito.Mockito.verifyNoInteractions(revoker);
    }

    @Test
    @DisplayName("#180 期限切れトークンは401だがrevokerは呼ばない")
    void 期限切れトークンはrevokerを呼ばない() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime past = OffsetDateTime.now().minusDays(1);
      RefreshToken expired = stored(familyId, null, null, past);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

      assertThatThrownBy(() -> refreshTokenService.rotate("expired-token"))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

      org.mockito.Mockito.verifyNoInteractions(revoker);
    }

    /** #181 markUsedが0件更新（同時リクエストのレース負け）。issue（新トークン発行）に進まない。 */
    @Test
    @DisplayName("#181 markUsedが0件更新なら401。insertは呼ばれない")
    void 競合でレースに負ける() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken usable = stored(familyId, null, null, future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(usable));
      when(refreshTokenMapper.markUsed(eq(1L), any())).thenReturn(0);

      assertThatThrownBy(() -> refreshTokenService.rotate("valid-token"))
          .isInstanceOf(UnauthorizedException.class)
          .extracting(e -> ((com.example.snstimeline.common.ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

      verify(refreshTokenMapper, never()).insert(any());
    }

    /** #182 ファミリーIDは引き継ぐ（issueNewFamilyとの対比。ログイン1回分の連鎖を追跡するため）。 */
    @Test
    @DisplayName("#182 正常なローテーションではファミリーIDが引き継がれる")
    void ファミリーIDは引き継がれる() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken usable = stored(familyId, null, null, future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(usable));
      when(refreshTokenMapper.markUsed(eq(1L), any())).thenReturn(1);

      RefreshTokenService.RotationResult result = refreshTokenService.rotate("valid-token");

      assertThat(result.userId()).isEqualTo(USER_ID);
      ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
      verify(refreshTokenMapper).insert(captor.capture());
      assertThat(captor.getValue().familyId()).isEqualTo(familyId);
    }

    /** #183 生成される生トークンの形。URLセーフBase64・パディング無しで32バイト→43文字。 */
    @Test
    @DisplayName("#183 生成される生トークンは43文字でURLセーフ（=,+,/を含まない）")
    void 生トークンの形式() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken usable = stored(familyId, null, null, future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(usable));
      when(refreshTokenMapper.markUsed(eq(1L), any())).thenReturn(1);

      RefreshTokenService.RotationResult result = refreshTokenService.rotate("valid-token");

      assertThat(result.refreshToken()).hasSize(43);
      assertThat(result.refreshToken()).doesNotContain("=", "+", "/");
    }

    /** #184 生トークンをそのまま保存しない。DBに渡るのはSHA-256ハッシュ（生トークンとは一致しない）。 */
    @Test
    @DisplayName("#184 insertに渡るtokenHashは生トークンと一致しない（ハッシュ化されている）")
    void 生トークンはそのまま保存されない() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken usable = stored(familyId, null, null, future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(usable));
      when(refreshTokenMapper.markUsed(eq(1L), any())).thenReturn(1);

      RefreshTokenService.RotationResult result = refreshTokenService.rotate("valid-token");

      ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
      verify(refreshTokenMapper).insert(captor.capture());
      assertThat(captor.getValue().tokenHash()).isNotEqualTo(result.refreshToken());
      assertThat(captor.getValue().id()).isNull();
    }

    /** #185 有効期限はコンストラクタで渡した日数ぶん先。 */
    @Test
    @DisplayName("#185 expiresAtはnow+expirationDays")
    void 有効期限は指定日数ぶん先() {
      UUID familyId = UUID.randomUUID();
      OffsetDateTime future = OffsetDateTime.now().plusDays(1);
      RefreshToken usable = stored(familyId, null, null, future);
      when(refreshTokenMapper.findByTokenHash(anyString())).thenReturn(Optional.of(usable));
      when(refreshTokenMapper.markUsed(eq(1L), any())).thenReturn(1);

      refreshTokenService.rotate("valid-token");

      ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
      verify(refreshTokenMapper).insert(captor.capture());
      OffsetDateTime expiresAt = captor.getValue().expiresAt();
      assertThat(expiresAt)
          .isCloseTo(
              OffsetDateTime.now().plusDays(14), within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    private org.assertj.core.data.TemporalUnitWithinOffset within(
        long value, java.time.temporal.ChronoUnit unit) {
      return new org.assertj.core.data.TemporalUnitWithinOffset(value, unit);
    }
  }

  @Nested
  @DisplayName("新規ファミリーの発行")
  class IssueNewFamily {

    /** #186 issueNewFamilyは毎回新しいUUIDをファミリーIDにする（rotateとの対比）。 */
    @Test
    @DisplayName("#186 issueNewFamilyは新しいファミリーIDでinsertする")
    void 新しいファミリーで発行される() {
      String token = refreshTokenService.issueNewFamily(USER_ID);

      ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
      verify(refreshTokenMapper).insert(captor.capture());
      assertThat(captor.getValue().familyId()).isNotNull();
      assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
      assertThat(token).hasSize(43);
    }
  }

  @Nested
  @DisplayName("全ファミリー失効")
  class RevokeAll {

    @Test
    @DisplayName("revokeAllはrevokeAllByUserIdに委譲する")
    void 全失効はマッパーに委譲される() {
      refreshTokenService.revokeAll(USER_ID);

      verify(refreshTokenMapper).revokeAllByUserId(eq(USER_ID), any());
    }
  }
}
