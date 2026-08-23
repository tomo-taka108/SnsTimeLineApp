package com.example.snstimeline.auth;

import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * リフレッシュトークンの発行・検証・失効（docs/09_decision_log.md D-29）。
 *
 * <p><b>なぜJWTではなく不透明トークン（ランダム文字列）なのか</b>: リフレッシュトークンをJWTにすると、アクセストークンと同じく「発行したら失効できない」
 * 問題を抱えることになる。長命なトークンほど失効できることが重要なので、 DBに状態を持てる不透明トークンにする。
 *
 * <p><b>なぜハッシュで保存するのか</b>: DBが漏洩したときに、そのままリフレッシュに使われないようにするため。
 * パスワードと違いランダム256bitなので総当たりが成立せず、BCryptのような 低速ハッシュは不要。SHA-256で足りる。
 */
@Service
public class RefreshTokenService {

  /** 生成するトークンのバイト数。256bit あれば総当たりは成立しない。 */
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenMapper refreshTokenMapper;
  private final RefreshTokenRevoker revoker;
  private final SecureRandom secureRandom = new SecureRandom();
  private final long expirationDays;

  public RefreshTokenService(
      RefreshTokenMapper refreshTokenMapper,
      RefreshTokenRevoker revoker,
      @Value("${app.jwt.refresh-expiration-days}") long expirationDays) {
    this.refreshTokenMapper = refreshTokenMapper;
    this.revoker = revoker;
    this.expirationDays = expirationDays;
  }

  /**
   * ログイン・新規登録時に、新しいファミリーのリフレッシュトークンを発行する。
   *
   * @return 生のトークン文字列。<b>この戻り値を最後に、生の値はサーバー側に残らない</b>
   */
  @Transactional
  public String issueNewFamily(Long userId) {
    return issue(userId, UUID.randomUUID());
  }

  /**
   * リフレッシュトークンを検証し、新しいトークンに差し替える（ローテーション）。
   *
   * <p><b>使い捨てにする理由</b>: 1回使ったトークンを無効にしておくと、盗まれたトークンが後から 使われたときに「使用済みのはずのものが再提示された」と検知できる。
   *
   * @return 新しい生のリフレッシュトークンと、その持ち主のユーザーID
   */
  @Transactional
  public RotationResult rotate(String rawToken) {
    OffsetDateTime now = OffsetDateTime.now();

    RefreshToken stored =
        refreshTokenMapper
            .findByTokenHash(hash(rawToken))
            .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN));

    // 【盗用検知】使用済みトークンの再提示。
    // 正常なクライアントは1つのトークンを2回使わない。2回目が来たということは、
    // トークンが漏れて攻撃者と正規ユーザーの両方が使っている可能性が高い。
    // どちらが攻撃者か判別できないので、そのログインに紐づく全トークンを失効させ、
    // 両方に再ログインを強制する。
    if (stored.isUsed()) {
      // 別トランザクションで失効させる。この直後に投げる401でロールバックされると
      // 失効そのものが巻き戻ってしまうため（RefreshTokenRevoker のコメント参照）。
      revoker.revokeFamilyInNewTransaction(stored.familyId(), stored.userId());
      throw new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (!stored.isUsable(now)) {
      // 失効済み・期限切れ。理由はクライアントに伝えない（統一して401）
      throw new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // used_at IS NULL を条件にした UPDATE。同時リクエストは1つしか成功しない。
    if (refreshTokenMapper.markUsed(stored.id(), now) == 0) {
      throw new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ファミリーIDは引き継ぐ。ログイン1回分の連鎖を1つの単位として追跡するため
    String newToken = issue(stored.userId(), stored.familyId());
    return new RotationResult(stored.userId(), newToken);
  }

  /** ログアウト。そのユーザーの有効なリフレッシュトークンをすべて失効させる。 */
  @Transactional
  public void revokeAll(Long userId) {
    refreshTokenMapper.revokeAllByUserId(userId, OffsetDateTime.now());
  }

  private String issue(Long userId, UUID familyId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    // URLセーフなBase64。パディング（=）はURLやヘッダーで扱いにくいので外す
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    refreshTokenMapper.insert(
        new RefreshToken(
            null,
            userId,
            hash(rawToken),
            familyId,
            OffsetDateTime.now().plusDays(expirationDays),
            null,
            null,
            null));

    return rawToken;
  }

  /** SHA-256 の16進表現。DBに入れるのは常にこの値で、生のトークンは保存しない。 */
  private String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 は Java の標準実装に必ず含まれるため、実際には到達しない
      throw new IllegalStateException("SHA-256 が利用できません", e);
    }
  }

  /** ローテーションの結果。 */
  public record RotationResult(Long userId, String refreshToken) {}
}
