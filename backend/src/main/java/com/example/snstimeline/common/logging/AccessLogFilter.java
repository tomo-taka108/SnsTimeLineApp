package com.example.snstimeline.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 1リクエスト1行のアクセスログ（docs/12_logging_and_operations.md 3章）。
 *
 * <p>導入前は成功したリクエストのログが1行も残らず、「誰が・どのAPIを・何msで叩いたか」が分からなかった。 このフィルタがそれに応える。
 *
 * <p><b>クエリ文字列は出さない。</b> {@code GET /users?q=...} の検索語は個人情報になりうる （docs/04_data_model.md 6.5
 * のアカウント列挙と同じ懸念）。{@link HttpServletRequest#getRequestURI()} のみを使い、{@code getRequestURL()} や {@code
 * getQueryString()} は使わない。
 *
 * <p>ヘッダは一切出さない。{@code Authorization} を出さないための最も確実な方法は「そもそも出さない」こと。
 */
@Component
public class AccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long start = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = System.currentTimeMillis() - start;
      int status = response.getStatus();
      String method = request.getMethod();
      String path = request.getRequestURI();
      if (status >= 500) {
        log.warn(
            "access method={} path={} status={} durationMs={}", method, path, status, durationMs);
      } else {
        log.info(
            "access method={} path={} status={} durationMs={}", method, path, status, durationMs);
      }
    }
  }
}
