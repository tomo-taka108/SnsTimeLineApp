package com.example.snstimeline.auth;

import com.example.snstimeline.auth.dto.AuthResponse;
import com.example.snstimeline.auth.dto.LoginRequest;
import com.example.snstimeline.auth.dto.RefreshRequest;
import com.example.snstimeline.auth.dto.SignupRequest;
import com.example.snstimeline.auth.dto.TokenResponse;
import com.example.snstimeline.user.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "認証", description = "新規登録・ログイン・トークン再発行・ログアウト")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** #1 新規登録。 */
  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  @SecurityRequirements
  @Operation(
      summary = "新規登録",
      description = "メールアドレス・ユーザー名・パスワードでアカウントを作成し、アクセストークンとリフレッシュトークンを返す。認証不要。")
  @ApiResponse(responseCode = "201", description = "登録成功")
  @ApiResponse(responseCode = "400", description = "入力値が不正（VALIDATION_ERROR）")
  @ApiResponse(
      responseCode = "409",
      description = "メールアドレスまたはユーザー名が既に使われている（EMAIL_ALREADY_EXISTS / USERNAME_ALREADY_EXISTS）")
  public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
    return authService.signup(request);
  }

  /** #2 ログイン。 */
  @PostMapping("/login")
  @SecurityRequirements
  @Operation(
      summary = "ログイン",
      description =
          """
          メールアドレスとパスワードで認証し、アクセストークン（15分）と
          リフレッシュトークン（14日）を返す。認証不要。

          ここで得た `accessToken` を画面右上の **Authorize** に貼ると、
          以降のAPIを試せるようになる。
          """)
  @ApiResponse(responseCode = "200", description = "ログイン成功")
  @ApiResponse(
      responseCode = "401",
      description = "メールアドレスまたはパスワードが違う（INVALID_CREDENTIALS）。どちらが違うかは示さない")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  /** #3 現在のユーザー情報。 */
  @GetMapping("/me")
  @Operation(summary = "ログイン中のユーザー情報", description = "アクセストークンから特定したユーザー自身の情報を返す。")
  @ApiResponse(responseCode = "200", description = "取得成功")
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
  @SecurityRequirements
  @Operation(
      summary = "トークン再発行",
      description =
          """
          リフレッシュトークンを使って、新しいアクセストークンとリフレッシュトークンを発行する。認証不要。

          **リフレッシュトークンは1回で使い捨て（ローテーション）。**
          クライアントは古い値を破棄し、返ってきた新しい値を保存し直すこと。

          使用済みのトークンが再提示された場合、そのログインに由来するトークンをすべて失効させる
          （盗用の可能性が高いため、安全側に倒す）。
          """)
  @ApiResponse(responseCode = "200", description = "再発行成功")
  @ApiResponse(responseCode = "401", description = "リフレッシュトークンが無効・期限切れ・使用済み（UNAUTHENTICATED）")
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
  @Operation(
      summary = "ログアウト",
      description =
          """
          そのユーザーのリフレッシュトークンをすべて失効させる。**認証が必要。**

          誰のトークンを失効させるかはアクセストークンから決めるため、
          リフレッシュトークンをボディで受け取らない
          （他人のトークンを送りつけて失効させる妨害を防ぐ）。
          """)
  @ApiResponse(responseCode = "204", description = "ログアウト成功（レスポンスボディなし）")
  public void logout(@AuthenticationPrincipal AuthPrincipal principal) {
    authService.logout(principal.userId());
  }
}
