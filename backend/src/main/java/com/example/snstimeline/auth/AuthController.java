package com.example.snstimeline.auth;

import com.example.snstimeline.auth.dto.AuthResponse;
import com.example.snstimeline.auth.dto.LoginRequest;
import com.example.snstimeline.auth.dto.SignupRequest;
import com.example.snstimeline.user.dto.UserSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認証API（docs/05_api_design.md #1〜#3）。
 *
 * <p>ベースパスはコントローラ側に書く。server.servlet.context-path を使うと SecurityConfig の requestMatchers
 * がコンテキストパスを除いたパスと突き合わせになり、 設定ミス（signupが認証必須になる等）に気づきにくいため。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** #1 新規登録。 */
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
    return authService.signup(request);
  }

  /** #2 ログイン。 */
  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /** #3 現在のユーザー情報。 */
  @GetMapping("/me")
  public UserSummary me(@AuthenticationPrincipal AuthPrincipal principal) {
    return authService.getMe(principal.userId());
  }
}
