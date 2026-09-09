package com.example.snstimeline.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * リクエストIDを発番し、MDCとレスポンスヘッダに載せる（docs/12_logging_and_operations.md 4章、D-63）。
 *
 * <p>認証より前段で動く必要がある（401のログにもリクエストIDを載せたいため）。Spring Securityの フィルタチェーンは {@code @Order}
 * ではなく明示的な登録順で決まるため、{@code SecurityConfig} で 最初に実行されるフィルタとして {@code .addFilterBefore(...,
 * CorsFilter.class)} 相当の位置に差す。
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

  private static final String HEADER = "X-Request-Id";

  /**
   * 外部から受け取ったリクエストIDの許容パターン。
   *
   * <p><b>ここでの検証を省いてはいけない。</b> {@code X-Request-Id} はクライアントが自由に設定できる値であり、無検証でそのままMDC・ログへ書き込むと、
   * 改行やログの区切り文字を仕込んだ値でログ行を偽造される（ログインジェクション）。 英数字とハイフンのみ・64文字以内に制限し、外れる場合は自前で発番し直す。
   */
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(HEADER));
    try {
      MDC.put(RequestContext.REQUEST_ID_KEY, requestId);
      response.setHeader(HEADER, requestId);
      filterChain.doFilter(request, response);
    } finally {
      // Tomcatはスレッドを使い回すため、消し忘れると次のリクエストに前の人の
      // リクエストID・ユーザーIDが漏れる。必ず finally で消す（docs/12_logging_and_operations.md 4章）。
      MDC.remove(RequestContext.REQUEST_ID_KEY);
    }
  }

  private static String resolveRequestId(String provided) {
    if (provided != null && SAFE_REQUEST_ID.matcher(provided).matches()) {
      return provided;
    }
    return UUID.randomUUID().toString();
  }
}
