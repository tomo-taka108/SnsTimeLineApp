package com.example.snstimeline.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * refresh_tokens テーブルに対応するドメインモデル（docs/09_decision_log.md D-29）。
 *
 * <p><b>生のトークン文字列はここに持たない。</b> 保存するのは SHA-256 ハッシュだけであり、生の値はレスポンスで一度返した後はサーバー側に残らない。
 */
public record RefreshToken(
    Long id,
    Long userId,
    String tokenHash,
    UUID familyId,
    OffsetDateTime expiresAt,
    OffsetDateTime usedAt,
    OffsetDateTime revokedAt,
    OffsetDateTime createdAt) {

  /** ローテーションで使用済みになっていないか。 */
  public boolean isUsed() {
    return usedAt != null;
  }

  /** ログアウトや盗用検知で失効させられていないか。 */
  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(OffsetDateTime now) {
    return expiresAt.isBefore(now);
  }

  /** リフレッシュに使える状態か。 */
  public boolean isUsable(OffsetDateTime now) {
    return !isUsed() && !isRevoked() && !isExpired(now);
  }
}
