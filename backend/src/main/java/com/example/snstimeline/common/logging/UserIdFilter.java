package com.example.snstimeline.common.logging;

import com.example.snstimeline.auth.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 認証済みユーザーIDをMDCに載せる（docs/12_logging_and_operations.md 4章、D-63）。
 *
 * <p><b>{@code JwtAuthenticationFilter} より後ろで動く必要がある。</b> {@code SecurityContextHolder} に {@link
 * AuthPrincipal} が入るのはそのフィルタの中であり、 これより前に動いても常に未認証として扱われてしまう。 {@code SecurityConfig} で {@code
 * .addFilterAfter(userIdFilter, JwtAuthenticationFilter.class)} として登録する。
 *
 * <p>未認証のリクエストでは何もしない（MDCにキー自体を入れない）。ECS構造化ログでは値が無ければ
 * フィールドごと出ないため、「未認証だった」ことと「userIdがnullだった」ことを区別する必要がない。
 */
@Component
public class UserIdFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      resolveUserId()
          .ifPresent(userId -> MDC.put(RequestContext.USER_ID_KEY, String.valueOf(userId)));
      filterChain.doFilter(request, response);
    } finally {
      // RequestIdFilterと同じ理由でfinally必須（スレッド使い回しによる漏洩を防ぐ）
      MDC.remove(RequestContext.USER_ID_KEY);
    }
  }

  private static Optional<Long> resolveUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthPrincipal principal) {
      return Optional.ofNullable(principal.userId());
    }
    return Optional.empty();
  }
}
