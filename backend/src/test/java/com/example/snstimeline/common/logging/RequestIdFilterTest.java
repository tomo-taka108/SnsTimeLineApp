package com.example.snstimeline.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link RequestIdFilter} の単体テスト（docs/11_test_design.md 25章、ケース #460〜）。
 *
 * <p>Spring全体を起動するとMDCへの副作用が他のフィルタと混ざって検証しづらいため、 {@code MockHttpServletRequest} / {@code
 * MockFilterChain} を直接使う単体テストにする。
 */
class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  /** 各テストの後にMDCを掃除する。フィルタのfinallyが効かなかった場合にテスト間で汚染が伝播しないための保険。 */
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Nested
  @DisplayName("発番")
  class Issue {

    /** #460 X-Request-Id ヘッダが無ければ自前でUUIDを発番する。 */
    @Test
    @DisplayName("#460 ヘッダが無ければUUIDを発番する")
    void ヘッダが無ければ発番() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      String header = response.getHeader("X-Request-Id");
      assertThat(header).isNotBlank();
      assertThat(java.util.UUID.fromString(header)).isNotNull();
    }

    /** #461 有効な X-Request-Id ヘッダはそのまま採用する（ALB/フロントからの伝播に備える）。 */
    @Test
    @DisplayName("#461 有効なヘッダはそのまま採用する")
    void 有効なヘッダは採用() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Request-Id", "client-supplied-id-123");
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(response.getHeader("X-Request-Id")).isEqualTo("client-supplied-id-123");
    }
  }

  @Nested
  @DisplayName("ログインジェクション対策（不正な値の破棄）")
  class Validation {

    /**
     * #462 <b>改行を含む値は採用せず、自前で発番し直す。</b>
     *
     * <p>ここが本章の中核。外部から受け取った値を無検証でMDCへ入れると、改行文字を仕込んだ値で ログ行を偽造できる（ログインジェクション）。
     */
    @Test
    @DisplayName("#462 改行を含む値は破棄し自前で発番する")
    void 改行を含む値は破棄() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Request-Id", "evil\nWARN fake-log-line injected");
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      String header = response.getHeader("X-Request-Id");
      assertThat(header).doesNotContain("\n").doesNotContain("evil");
      assertThat(java.util.UUID.fromString(header)).isNotNull();
    }

    /** #463 64文字を超える値は破棄する。 */
    @Test
    @DisplayName("#463 65文字の値は破棄し自前で発番する")
    void 長すぎる値は破棄() throws Exception {
      String tooLong = "a".repeat(65);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Request-Id", tooLong);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(response.getHeader("X-Request-Id")).isNotEqualTo(tooLong);
    }

    /** #464 64文字ちょうどの英数字・ハイフンは境界内として採用する（#463と対）。 */
    @Test
    @DisplayName("#464 64文字ちょうどは採用する")
    void ちょうど64文字は採用() throws Exception {
      String exactly64 = "a".repeat(64);
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Request-Id", exactly64);
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(response.getHeader("X-Request-Id")).isEqualTo(exactly64);
    }

    /** #465 空文字は破棄する。 */
    @Test
    @DisplayName("#465 空文字は破棄し自前で発番する")
    void 空文字は破棄() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Request-Id", "");
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(response.getHeader("X-Request-Id")).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("MDCの後始末")
  class MdcCleanup {

    /**
     * #466 <b>最も危険な失敗を防ぐテスト。</b>
     *
     * <p>Tomcatはスレッドを使い回すため、{@code finally} でMDCを消し忘れると 次のリクエストに前のリクエストIDが漏れる。 doFilter完了後にMDCへ
     * requestId が残っていないことを確認する。
     */
    @Test
    @DisplayName("#466 doFilter完了後はMDCからrequestIdが消えている")
    void 完了後はMDCから消える() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      MockHttpServletResponse response = new MockHttpServletResponse();
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(request, response, chain);

      assertThat(MDC.get("requestId")).isNull();
    }

    /**
     * #467 <b>チェーンの内側では、そのリクエストのIDがMDCに入っている。</b>
     *
     * <p>#466（後で消える）と対にして、「一時的にでも正しく入っている」ことを確認する。 チェーンの内側でMDCの値を記録するカスタムFilterChainを使う。
     */
    @Test
    @DisplayName("#467 チェーンの内側ではMDCにrequestIdが入っている")
    void チェーン内ではMDCに入っている() throws Exception {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("X-Request-Id", "inside-the-chain-id");
      MockHttpServletResponse response = new MockHttpServletResponse();
      java.util.concurrent.atomic.AtomicReference<String> seenInsideChain =
          new java.util.concurrent.atomic.AtomicReference<>();
      MockFilterChain chain =
          new MockFilterChain() {
            @Override
            public void doFilter(
                jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
              seenInsideChain.set(MDC.get("requestId"));
            }
          };

      filter.doFilter(request, response, chain);

      assertThat(seenInsideChain.get()).isEqualTo("inside-the-chain-id");
    }

    /**
     * #468 <b>連続する2リクエストで前の値が漏れないこと（同一スレッドで再現）。</b>
     *
     * <p>スレッド使い回しそのものはテストで強制できないが、同じ{@code RequestIdFilter}インスタンスを
     * 同一スレッドで2回連続実行することで、消し忘れがあれば1回目の値が2回目に 漏れ出す状況を再現する。
     */
    @Test
    @DisplayName("#468 前のリクエストのIDが次のリクエストに漏れない")
    void 前のリクエストIDが漏れない() throws Exception {
      MockHttpServletRequest first = new MockHttpServletRequest();
      first.addHeader("X-Request-Id", "first-request-id");
      filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());

      MockHttpServletRequest second = new MockHttpServletRequest();
      MockHttpServletResponse secondResponse = new MockHttpServletResponse();
      java.util.concurrent.atomic.AtomicReference<String> seenInsideChain =
          new java.util.concurrent.atomic.AtomicReference<>();
      MockFilterChain chain =
          new MockFilterChain() {
            @Override
            public void doFilter(
                jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
              seenInsideChain.set(MDC.get("requestId"));
            }
          };

      filter.doFilter(second, secondResponse, chain);

      assertThat(seenInsideChain.get()).isNotEqualTo("first-request-id");
    }
  }
}
