package com.example.snstimeline.auth;

/**
 * 認証済みユーザー。ユーザーIDのみを持つ。
 *
 * <p>UserDetails / UserDetailsService は実装しない。ステートレスJWTでは署名済みの sub を信頼できるため、リクエストごとにDBを引く必要がなく、また
 * {@code UserDetails#getPassword()} が BCryptハッシュを毎リクエスト SecurityContext に載せることになるため。
 */
public record AuthPrincipal(Long userId) {}
