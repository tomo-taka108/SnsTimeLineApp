package com.example.snstimeline.auth;

import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 認証済みだが権限が無い場合に 403 を統一エラー形式で返す。AuthEntryPoint と同じ理由で必要。 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

  private final ObjectMapper objectMapper;

  public RestAccessDeniedHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    log.warn("権限エラー path={}", request.getRequestURI());

    ErrorResponse body = ErrorResponse.of(ErrorCode.FORBIDDEN, request.getRequestURI());
    response.setStatus(ErrorCode.FORBIDDEN.getStatus().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
