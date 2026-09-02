package com.example.snstimeline.support;

import com.example.snstimeline.auth.AuthPrincipal;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc に認証済みユーザーを載せる（docs/09_decision_log.md D-55）。
 *
 * <p><b>{@code @WithMockUser} は使えない。</b> あれが SecurityContext に入れるのは {@code UserDetails} か {@code
 * String} であり、{@link AuthPrincipal} ではない。 コントローラの {@code @AuthenticationPrincipal AuthPrincipal} には
 * null が注入され、 {@code principal.userId()} が NPE を起こして<b>認可エラーではなく500</b>になる。
 * 落ちた理由が分かりにくい形で失敗するため、原因究明に時間を取られる。
 *
 * <p>ここで組む Authentication は {@code JwtAuthenticationFilter} が本番で組むものと同一 （principal は
 * AuthPrincipal、credentials は null、権限は空リスト。管理者ロールを持たない設計 D-14）。
 *
 * <p>実JWTを発行してヘッダに載せる方法も採れるが、こちらを既定にする。 テストごとに JwtTokenProvider を注入する必要がなく、アクセストークンの15分という
 * <b>時間依存が入らない</b>ため。JWT の検証そのものは {@code JwtAuthenticationFilter} を 対象にしたテストで別途行う。
 *
 * <p>認証なしの場合はこれを付けない。Security チェーンは本物が動くので {@code AuthEntryPoint} が 401 の JSON を返す。
 */
public final class TestAuth {

  private TestAuth() {}

  /** 指定したユーザーIDで認証済みにする。 */
  public static RequestPostProcessor as(long userId) {
    return SecurityMockMvcRequestPostProcessors.authentication(
        new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId), null, List.of()));
  }
}
