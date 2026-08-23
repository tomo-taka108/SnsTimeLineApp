package com.example.snstimeline.auth;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 盗用検知時のファミリー失効を、呼び出し元とは別のトランザクションで確定させる。
 *
 * <p><b>なぜクラスを分けるのか（実装時に踏んだ罠）</b>: 盗用検知は「失効させてから401を投げる」という流れになる。ところが 401（RuntimeException）は
 * トランザクションをロールバックさせるため、 同じトランザクション内で失効させると<b>その失効ごと巻き戻る</b>。 結果、ログには「失効させました」と出るのに DB は無傷、という状態になる。
 *
 * <p>{@code REQUIRES_NEW} で独立したトランザクションにすれば、例外を投げても 失効は確定したまま残る。
 *
 * <p>Spring のトランザクションはプロキシ経由で効くため、<b>同一クラス内の メソッド呼び出しでは {@code REQUIRES_NEW} が無視される</b>。別の Bean
 * に切り出す必要があるのはそのため。
 */
@Component
public class RefreshTokenRevoker {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenRevoker.class);

  private final RefreshTokenMapper refreshTokenMapper;

  public RefreshTokenRevoker(RefreshTokenMapper refreshTokenMapper) {
    this.refreshTokenMapper = refreshTokenMapper;
  }

  /**
   * ファミリーを失効させ、その場でコミットする。
   *
   * @param userId ログ用。内部の識別子であり個人情報ではないため記録してよい
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamilyInNewTransaction(UUID familyId, Long userId) {
    int revoked = refreshTokenMapper.revokeFamily(familyId, OffsetDateTime.now());
    // トークンそのものは絶対にログへ出さない（docs/06_non_functional.md 5.2）
    log.warn(
        "使用済みリフレッシュトークンが再提示されたため、ファミリーを失効させました" + " userId={} familyId={} revokedCount={}",
        userId,
        familyId,
        revoked);
  }
}
