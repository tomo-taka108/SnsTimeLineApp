package com.example.snstimeline.auth;

import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 未認証時に 401 を統一エラー形式で返す。
 *
 * <p><b>これが無いとボディが空の401になる。</b> 認証エラーはフィルタチェーン内で発生するため、 DispatcherServlet
 * まで到達せず @RestControllerAdvice では捕捉できない。統一エラー形式 （F-CO-01）を守るにはここで書き出す必要がある。
 */
@Component
public class AuthEntryPoint implements AuthenticationEntryPoint {

  private static final Logger log = LoggerFactory.getLogger(AuthEntryPoint.class);

  private final ObjectMapper objectMapper;

  public AuthEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    // 認証失敗は WARN（docs/06_non_functional.md 5.2）。
    // トークン・メールアドレスは出さない。パスのみ。
    log.warn("認証エラー path={}", request.getRequestURI());

    ErrorResponse body = ErrorResponse.of(ErrorCode.UNAUTHENTICATED, request.getRequestURI());
    response.setStatus(ErrorCode.UNAUTHENTICATED.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
