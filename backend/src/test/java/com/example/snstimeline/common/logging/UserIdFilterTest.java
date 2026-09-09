package com.example.snstimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.snstimeline.auth.AuthPrincipal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** {@link UserIdFilter} の単体テスト（docs/11_test_design.md 25章、ケース #460〜）。 */
class UserIdFilterTest {

  private final UserIdFilter filter = new UserIdFilter();

  @AfterEach
  void clearContext() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("認証状態による分岐")
  class AuthenticationBranch {

    /** #469 認証済みなら SecurityContext の userId が MDC に入る。 */
    @Test
    @DisplayName("#469 認証済みならuserIdがMDCに入る")
    void 認証済みならuserIdが入る() throws Exception {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(new AuthPrincipal(42L), null, List.of()));
      AtomicReference<String> seenInsideChain = new AtomicReference<>();
      MockFilterChain chain =
          new MockFilterChain() {
            @Override
            public void doFilter(
                jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
              seenInsideChain.set(MDC.get("userId"));
            }
          };

      filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

      assertThat(seenInsideChain.get()).isEqualTo("42");
    }

    /** #470 未認証なら何もしない（MDCにキー自体が入らない）。 */
    @Test
    @DisplayName("#470 未認証ならuserIdはMDCに入らない")
    void 未認証ならuserIdは入らない() throws Exception {
      AtomicReference<String> seenInsideChain = new AtomicReference<>();
      MockFilterChain chain =
          new MockFilterChain() {
            @Override
            public void doFilter(
                jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
              seenInsideChain.set(MDC.get("userId"));
            }
          };

      filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

      assertThat(seenInsideChain.get()).isNull();
    }
  }

  @Nested
  @DisplayName("MDCの後始末")
  class MdcCleanup {

    /** #471 doFilter完了後はMDCからuserIdが消えている（RequestIdFilterと同じ理由）。 */
    @Test
    @DisplayName("#471 完了後はMDCからuserIdが消える")
    void 完了後はMDCから消える() throws Exception {
      SecurityContextHolder.getContext()
          .setAuthentication(
              new UsernamePasswordAuthenticationToken(new AuthPrincipal(7L), null, List.of()));

      filter.doFilter(
          new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

      assertThat(MDC.get("userId")).isNull();
    }
  }
}
