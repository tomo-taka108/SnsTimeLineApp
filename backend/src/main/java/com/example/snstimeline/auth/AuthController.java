package com.example.snstimeline.auth;

import com.example.snstimeline.auth.dto.AuthResponse;
import com.example.snstimeline.auth.dto.LoginRequest;
import com.example.snstimeline.auth.dto.RefreshRequest;
import com.example.snstimeline.auth.dto.SignupRequest;
import com.example.snstimeline.auth.dto.TokenResponse;
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
 * 認証API（docs/05_api_design.md #1〜#5）。
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

  /**
   * #4 トークン再発行。
   *
   * <p><b>認証不要（permitAll）。</b> 期限切れのアクセストークンしか持っていない状態で 呼ぶAPIなので、アクセストークンを要求しては意味がない。
   * リクエストボディのリフレッシュトークン自体が認証情報になる。
   */
  @PostMapping("/refresh")
  public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request.refreshToken());
  }

  /**
   * #5 ログアウト。
   *
   * <p>こちらは認証が必要。「誰のトークンを失効させるか」をアクセストークンから決めるため、 リフレッシュトークンをボディで受け取る必要がない
   * （他人のリフレッシュトークンを送りつけて失効させる、という妨害も防げる）。
   */
  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@AuthenticationPrincipal AuthPrincipal principal) {
    authService.logout(principal.userId());
  }
}
