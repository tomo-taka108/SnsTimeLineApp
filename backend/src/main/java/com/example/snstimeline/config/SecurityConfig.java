package com.example.snstimeline.config;

import com.example.snstimeline.auth.AuthEntryPoint;
import com.example.snstimeline.auth.JwtAuthenticationFilter;
import com.example.snstimeline.auth.RestAccessDeniedHandler;
import com.example.snstimeline.common.logging.AccessLogFilter;
import com.example.snstimeline.common.logging.RequestIdFilter;
import com.example.snstimeline.common.logging.UserIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.CorsFilter;

/** Spring Security の設定（docs/07_architecture.md 4.1, docs/06_non_functional.md 3章）。 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final AuthEntryPoint authEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;
  private final CorsConfigurationSource corsConfigurationSource;
  private final RequestIdFilter requestIdFilter;
  private final UserIdFilter userIdFilter;
  private final AccessLogFilter accessLogFilter;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      AuthEntryPoint authEntryPoint,
      RestAccessDeniedHandler restAccessDeniedHandler,
      CorsConfigurationSource corsConfigurationSource,
      RequestIdFilter requestIdFilter,
      UserIdFilter userIdFilter,
      AccessLogFilter accessLogFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.authEntryPoint = authEntryPoint;
    this.restAccessDeniedHandler = restAccessDeniedHandler;
    this.corsConfigurationSource = corsConfigurationSource;
    this.requestIdFilter = requestIdFilter;
    this.userIdFilter = userIdFilter;
    this.accessLogFilter = accessLogFilter;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // CorsFilter は UsernamePasswordAuthenticationFilter より前に入る。
        // JWTフィルタはその直前に差すので、必ず CORS の後になる。
        // これにより「プリフライトが401になる」ハマりどころを避ける
        // （docs/05_api_design.md 7章）。
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        // JWT + allowCredentials:false のためCSRF対策は不要
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(authEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth
                    // プリフライトを素通しする（二重の保険）
                    .requestMatchers(CorsUtils::isPreFlightRequest)
                    .permitAll()
                    // /refresh は認証不要。期限切れのアクセストークンしか持たない
                    // 状態で呼ぶAPIなので、有効なアクセストークンを要求しては意味がない。
                    // 認証情報はボディのリフレッシュトークンそのもの。
                    // なお /logout は認証必須（誰のトークンを失効させるかを
                    // アクセストークンから決めるため）で、ここには書かない。
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    // 画像配信（#26）は認証不要。<img src> は Authorization ヘッダを
                    // 付けられないため。本アプリの投稿はすべて公開なので、
                    // 画像だけを秘匿しても意味がない（docs/05_api_design.md #26）。
                    // アップロード（#25, POST）は認証必須のまま
                    .requestMatchers(HttpMethod.GET, "/api/v1/files/*")
                    .permitAll()
                    // API仕様書（Swagger UI）は認証不要。仕様を読むためだけにログインを
                    // 強制するのは本末転倒であり、そもそも仕様書はGitHub Pagesで公開している
                    // （docs/api/）。/swagger-ui.html はリダイレクト前のパスなので、
                    // /swagger-ui/** とは別に明記しないと拾えない
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // RequestIdFilter は全フィルタの中で最初に動く必要がある（CORSやAuth失敗時のログにも
        // リクエストIDを載せるため）。AccessLogFilter はその内側に差す。
        // 順序が重要: RequestIdFilterがMDCに載せてから内側のAccessLogFilterへ処理を渡し、
        // AccessLogFilterのfinallyでログを出した後にRequestIdFilterのfinallyでMDCを消す。
        // 逆順にすると、AccessLogFilterが1行ログを出す時点でMDCが既に消えており、
        // requestIdの無いアクセスログになってしまう（docs/12_logging_and_operations.md 4章）。
        .addFilterBefore(requestIdFilter, CorsFilter.class)
        .addFilterAfter(accessLogFilter, RequestIdFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // ユーザーIDはJWTフィルタが SecurityContextHolder にセットした後でなければ読めない
        .addFilterAfter(userIdFilter, JwtAuthenticationFilter.class);

    return http.build();
  }

  /** BCrypt。コストは Spring Security 既定の 10（docs/06_non_functional.md 3.1）。 */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
