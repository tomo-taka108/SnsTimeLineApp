package com.example.snstimeline.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT の生成と検証（docs/06_non_functional.md 3.2）。
 *
 * <p>ペイロードはユーザーIDのみ。JWTはBase64であり暗号化されていないため、 メールアドレスなどの個人情報を入れない。
 */
@Component
public class JwtTokenProvider {

  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;
  private final long expirationHours;

  public JwtTokenProvider(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-hours}") long expirationHours) {

    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < MIN_SECRET_BYTES) {
      // 起動を失敗させる。弱い鍵で署名し続けるより明確に落ちるほうが安全
      throw new IllegalStateException("JWT_SECRET は " + MIN_SECRET_BYTES + " バイト以上である必要があります");
    }
    // 256bit 未満なら jjwt が WeakKeyException を投げる
    this.key = Keys.hmacShaKeyFor(secretBytes);
    this.expirationHours = expirationHours;
  }

  /** ユーザーIDを sub に持つJWTを発行する。 */
  public String createToken(Long userId) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationHours, ChronoUnit.HOURS)))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * トークンを検証してユーザーIDを取り出す。検証に失敗したら empty を返す。
   *
   * <p><b>alg: none を拒否する仕組み（docs/06_non_functional.md 3.2 の要求）</b>:
   *
   * <ol>
   *   <li>{@code parseSignedClaims} は JWS を必須とする。alg:none の未署名JWTは {@code UnsupportedJwtException}
   *       になる（受け入れるには parser に明示的に {@code .unsecured()} を呼ぶ必要があり、呼んでいない）
   *   <li>{@code verifyWith} が MAC 検証を強制する。検証を飛ばす経路が無い
   *   <li>{@code sig().add(HS256)} で受理するアルゴリズムを限定し、HS512 への すり替えや RS256→HS256 の混同攻撃も塞ぐ
   * </ol>
   *
   * <p>exp は jjwt が自動検証する（期限切れは ExpiredJwtException）。
   */
  public Optional<Long> validateAndGetUserId(String token) {
    try {
      Jws<Claims> jws =
          Jwts.parser()
              .verifyWith(key)
              .sig()
              .add(Jwts.SIG.HS256)
              .and()
              .build()
              .parseSignedClaims(token);
      return Optional.of(Long.valueOf(jws.getPayload().getSubject()));
    } catch (JwtException | IllegalArgumentException e) {
      // 例外の中身はログに出さない（トークンそのものが漏れるため）
      return Optional.empty();
    }
  }
}
