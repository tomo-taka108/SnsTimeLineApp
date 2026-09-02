package com.example.snstimeline.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * テスト基盤そのものの疎通確認。
 *
 * <p>ここが緑にならないと以降のテストは1本も書けない。逆にここが通れば、 コンテナ起動・Flyway適用・MyBatis配線・JWT設定の供給がすべて成立している。
 *
 * <p><b>H2 では実行できない機能を意図的に検証する。</b> テスト基盤が本物の PostgreSQL を使えていることの証明であり、
 * 同時に「H2に差し替えれば軽くなる」という将来の誘惑に対する歯止めにもなる（D-54）。
 */
@SpringBootTest
@Transactional
class TestInfrastructureTest extends AbstractIntegrationTest {

  @Autowired JdbcClient jdbc;
  @Autowired TestFixtures fixtures;

  @Test
  @DisplayName("Testcontainers の PostgreSQL に接続でき、バージョンが16である")
  void postgresに接続できる() {
    String version = jdbc.sql("SELECT version()").query(String.class).single();

    // docker-compose.yml と揃っていること。版がずれると挙動差を検出できない
    assertThat(version).contains("PostgreSQL 16");
  }

  @Test
  @DisplayName("Flyway のマイグレーションが V11 まで適用されている")
  void flywayが適用されている() {
    String latest =
        jdbc.sql("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1")
            .query(String.class)
            .single();

    assertThat(latest).isEqualTo("11");
  }

  @Test
  @DisplayName("pg_trgm 拡張が有効で similarity() を実行できる（H2では不可能）")
  void pgTrgmが使える() {
    // V10 で CREATE EXTENSION した拡張。UserMapper.searchUsers の ORDER BY が依存している
    Double score = jdbc.sql("SELECT similarity('田中たろう', 'たろう')").query(Double.class).single();

    assertThat(score).isGreaterThan(0.0);
  }

  @Test
  @DisplayName("行値比較でカーソルの条件を書ける（H2では不可能）")
  void 行値比較が使える() {
    // PostMapper.findTimeline の (created_at, id) < (?, ?) と同じ形。
    // 第1要素が等しいとき第2要素で決まる = タイブレーカーが効くことの最小確認
    Boolean result =
        jdbc.sql(
                "SELECT (timestamptz '2026-09-01 10:00:00+09', 5) < (timestamptz '2026-09-01 10:00:00+09', 9)")
            .query(Boolean.class)
            .single();

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("テストデータを投入でき、投稿とユーザーが結び付く")
  void フィクスチャでデータを作れる() {
    long userId = fixtures.user("alice");
    long postId = fixtures.post(userId, "テスト投稿");

    String body =
        jdbc.sql("SELECT body FROM posts WHERE id = ?").param(postId).query(String.class).single();

    assertThat(body).isEqualTo("テスト投稿");
    assertThat(fixtures.likeCountOf(postId)).isZero();
  }
}
