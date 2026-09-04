package com.example.snstimeline.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RefreshTokenRevoker} の単体テスト（docs/11_test_design.md 15.2節、ケース #187）。
 *
 * <p>{@code REQUIRES_NEW}（別トランザクション）の分離自体は単体テストでは検証できない。 委譲先と引数順のみを確認する。
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRevokerTest {

  @Mock private RefreshTokenMapper refreshTokenMapper;

  @InjectMocks private RefreshTokenRevoker revoker;

  @Test
  @DisplayName(
      "#187 revokeFamilyInNewTransactionはrefreshTokenMapper.revokeFamily(familyId, now)に委譲する")
  void ファミリー失効はマッパーに委譲される() {
    UUID familyId = UUID.randomUUID();
    long userId = 5L;

    revoker.revokeFamilyInNewTransaction(familyId, userId);

    verify(refreshTokenMapper).revokeFamily(eq(familyId), any(OffsetDateTime.class));
  }
}
