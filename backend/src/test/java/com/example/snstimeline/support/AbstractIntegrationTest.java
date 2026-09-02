package com.example.snstimeline.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 実DBを使うテストの共通基底（docs/09_decision_log.md D-54）。
 *
 * <p><b>H2は使えない。</b> Flyway の {@code CREATE EXTENSION pg_trgm}（V10）・部分インデックス（V3/V5/V9）・ {@code
 * text_pattern_ops}（V9）・POSIX正規表現の CHECK（V1）と、Mapper の行値比較 {@code (created_at, id) < (?, ?)}・{@code
 * INSERT ... RETURNING}・{@code similarity()} が再現できず、 V10 の時点でマイグレーションが停止してテストが1本も起動しない。
 *
 * <p><b>コンテナは static で1つだけ持ち、JVM全体で共有する。</b> {@code @Container} のインスタンスフィールドにすると
 * テストクラスごとに起動し直し、クラス数×起動時間が積み上がる。 停止は書かない（明示的に {@code stop()} すると最初のクラスが終わった時点で落ち、2クラス目が接続できなくなる）。
 * 後片付けは Testcontainers の Ryuk コンテナが JVM 終了時に行う。
 *
 * <p><b>Flyway は実質1回しか走らない。</b> コンテナが同一なので、2クラス目以降は Spring の コンテキストキャッシュが効き、仮に別コンテキストが作られても Flyway
 * は {@code flyway_schema_history} を見て 適用済みと判断する。
 *
 * <p>注意: Testcontainers 2.x では 1.x と artifactId・パッケージが異なり、self-type のジェネリクスも廃止された。 {@code
 * PostgreSQLContainer<?>} ではなく素の {@code PostgreSQLContainer} を使う。
 */
public abstract class AbstractIntegrationTest {

  /**
   * docker-compose.yml と同じ postgres:16 を使う。
   *
   * <p>版を合わせないと pg_trgm・行値比較・text_pattern_ops の挙動差を検出できず、 「テストは緑なのに開発環境で落ちる」が起きる。
   */
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

  static {
    POSTGRES.start();
  }

  /**
   * テスト用の設定を与える。
   *
   * <p>{@code app.jwt.secret} は application.yml で {@code ${JWT_SECRET}} と書かれており<b>既定値が無い</b>。 与えないと
   * {@link com.example.snstimeline.auth.JwtTokenProvider} のコンストラクタが例外を投げ、 ApplicationContext
   * の生成そのものが失敗する。32バイト以上必須。
   *
   * <p>ここに書くのは<b>テスト専用の固定文字列</b>であり、{@code .env} の本物の値とは無関係。 実在のシークレットをテストコードに書かないこと（CLAUDE.md 6章）。
   *
   * <p>{@code @TestPropertySource} ではなくこちらを使う理由: サブクラスが独自の {@code @TestPropertySource}
   * を付けると上書きで消えてしまうが、static メソッドのこれは 継承されて衝突しない。
   */
  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("app.jwt.secret", () -> "test-secret-for-junit-only-min-32-bytes-long");
    // アップロードの保存先を一時ディレクトリに向け、開発用の backend/uploads/ を汚さない
    registry.add(
        "app.storage.local-path",
        () -> System.getProperty("java.io.tmpdir") + "/snstimeline-test-uploads");
  }
}
