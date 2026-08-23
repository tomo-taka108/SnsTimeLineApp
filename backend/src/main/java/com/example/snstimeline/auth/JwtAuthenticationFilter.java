package com.example.snstimeline.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization: Bearer ヘッダーからJWTを取り出して認証状態を組み立てるフィルタ。
 *
 * <p>ヘッダーが無い場合・トークンが無効な場合でも例外は投げない。認証しないまま通し、 認可の判断は SecurityConfig の authorizeHttpRequests
 * に任せる（docs/07_architecture.md 4.1）。 保護されたパスなら AuthEntryPoint が 401 を返す。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String HEADER = "Authorization";
  private static final String PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;

  public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader(HEADER);
    if (header != null && header.startsWith(PREFIX)) {
      String token = header.substring(PREFIX.length());

      // 注意: sub はDBを引かずに信頼する。トークン発行後に論理削除されたユーザーも
      // このフィルタは通過するが、後段のサービスが deleted_at IS NULL 付きで
      // ユーザーを引くため 404 になる。退会機能は Phase3 のためMVPでは許容する。
      jwtTokenProvider
          .validateAndGetUserId(token)
          .ifPresent(
              userId -> {
                var authentication =
                    new UsernamePasswordAuthenticationToken(
                        new AuthPrincipal(userId), null, List.of()); // 権限は空（管理者ロールなし D-14）
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
              });
    }

    filterChain.doFilter(request, response);
  }
}
