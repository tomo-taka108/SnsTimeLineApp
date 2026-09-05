package com.example.snstimeline.support;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 統合テスト用のデータ投入。
 *
 * <p><b>Mapper を経由せず JdbcClient で直接 INSERT する。</b> 理由は2つ。
 *
 * <ol>
 *   <li>Mapper 自体をテスト対象にする回があり、準備に同じ Mapper を使うと 「壊れた Mapper で作ったデータを、壊れた Mapper で検証する」ことになる
 *   <li>{@code created_at} を明示指定できる。{@code PostMapper.insert} は {@code DEFAULT now()}
 *       に任せるため、カーソルのタイブレーカー検証ができない
 * </ol>
 *
 * <p>メールアドレスは必ず {@code example.com} を使い、実在の個人名・メールアドレスは使わない（CLAUDE.md 6章）。
 */
@Component
public class TestFixtures {

  private final JdbcClient jdbc;

  /** メール・ユーザー名の一意制約に引っかからないための連番。 */
  private final AtomicInteger seq = new AtomicInteger();

  public TestFixtures(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * ユーザーを1人作る。
   *
   * <p>{@code passwordHash} はダミー。ログイン検証をしないテストでは中身を問わない（BCryptの計算コストを避ける）。 パスワード照合を伴うテストは、実際に
   * {@code PasswordEncoder} で作った値を渡すこと。
   */
  public long user(String username) {
    int n = seq.incrementAndGet();
    return jdbc.sql(
            """
            INSERT INTO users (email, password_hash, username, display_name)
            VALUES (?, ?, ?, ?) RETURNING id
            """)
        .params(
            "user%d@example.com".formatted(n),
            "$2a$10$dummyhashfortestonly",
            username,
            username + "の表示名")
        .query(Long.class)
        .single();
  }

  /** 投稿を1件作る。{@code created_at} はDBの {@code now()} に任せる。 */
  public long post(long userId, String body) {
    return jdbc.sql("INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id")
        .params(userId, body)
        .query(Long.class)
        .single();
  }

  /**
   * {@code created_at} を明示して投稿を作る。
   *
   * <p>カーソルのタイブレーカー（同一 {@code created_at} で {@code id} が効くか、D-33）の検証に使う。 {@code PostMapper.insert}
   * は {@code DEFAULT now()} に任せるうえ、同一トランザクション内では {@code now()} が同じ値を返すため、素直に入れると意図した状況を作れない。
   *
   * <p><b>呼び出し側は {@code truncatedTo(ChronoUnit.MICROS)} した値を渡すこと。</b> {@code CursorCodec}
   * はマイクロ秒精度だが {@code OffsetDateTime.now()} はナノ秒精度を持つため、 丸めずに渡すとカーソルの往復で値が一致しなくなる。
   */
  public long postAt(long userId, String body, OffsetDateTime createdAt) {
    return jdbc.sql(
            """
            INSERT INTO posts (user_id, body, created_at, updated_at)
            VALUES (?, ?, ?, ?) RETURNING id
            """)
        .params(userId, body, createdAt, createdAt)
        .query(Long.class)
        .single();
  }

  /**
   * コメントを1件作り、{@code posts.comment_count} を +1 する。
   *
   * <p>カウンタも併せて更新するのは、本番の {@code CommentService.create} と同じ状態を作るため。 カウンタを更新しないと「削除で -1
   * されること」のテストが負数チェック制約に引っかかる。
   */
  public long comment(long postId, long userId, String body) {
    long id =
        jdbc.sql("INSERT INTO comments (post_id, user_id, body) VALUES (?, ?, ?) RETURNING id")
            .params(postId, userId, body)
            .query(Long.class)
            .single();
    jdbc.sql("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?")
        .param(postId)
        .update();
    return id;
  }

  /**
   * {@code created_at} を明示してコメントを作る。{@link #postAt} と対になる。
   *
   * <p>コメント一覧のカーソルは投稿とは逆の<b>昇順</b>（{@code ORDER BY c.created_at ASC, c.id ASC}）のため、 タイブレーカーの検証には同一
   * {@code created_at} のコメントを作る必要がある。
   *
   * <p><b>呼び出し側は {@code truncatedTo(ChronoUnit.MICROS)} した値を渡すこと</b>（{@link #postAt} と同じ理由）。
   */
  public long commentAt(long postId, long userId, String body, OffsetDateTime createdAt) {
    long id =
        jdbc.sql(
                """
                INSERT INTO comments (post_id, user_id, body, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """)
            .params(postId, userId, body, createdAt, createdAt)
            .query(Long.class)
            .single();
    jdbc.sql("UPDATE posts SET comment_count = comment_count + 1 WHERE id = ?")
        .param(postId)
        .update();
    return id;
  }

  /** いいねを1件作り、{@code posts.like_count} を +1 する。 */
  public void like(long postId, long userId) {
    jdbc.sql("INSERT INTO likes (post_id, user_id) VALUES (?, ?)").params(postId, userId).update();
    jdbc.sql("UPDATE posts SET like_count = like_count + 1 WHERE id = ?").param(postId).update();
  }

  /** {@code follower} が {@code followee} をフォローしている状態を作る（follows は非正規化カウンタを持たない）。 */
  public void follow(long followerId, long followeeId) {
    jdbc.sql("INSERT INTO follows (follower_id, followee_id) VALUES (?, ?)")
        .params(followerId, followeeId)
        .update();
  }

  /** アップロード済みファイルのメタ情報を作る。ファイル所有者チェック（403）のテストに使う。 */
  public long storedFile(long uploadedBy) {
    int n = seq.incrementAndGet();
    return jdbc.sql(
            """
            INSERT INTO stored_files
              (storage_type, storage_key, original_filename, content_type, size_bytes, uploaded_by)
            VALUES ('LOCAL', ?, 'test.jpg', 'image/jpeg', 1024, ?) RETURNING id
            """)
        .params("2026/09/01/test-%d.jpg".formatted(n), uploadedBy)
        .query(Long.class)
        .single();
  }

  /** 投稿を論理削除する。削除済みが一覧・詳細に出ないことの検証に使う（D-02）。 */
  public void softDeletePost(long postId) {
    jdbc.sql("UPDATE posts SET deleted_at = now() WHERE id = ?").param(postId).update();
  }

  /** ユーザーを論理削除（退会）する。退会ユーザーが検索・一覧に出ないことの検証に使う。 */
  public void softDeleteUser(long userId) {
    jdbc.sql("UPDATE users SET deleted_at = now() WHERE id = ?").param(userId).update();
  }

  /** 投稿の {@code like_count} を直接読む。Service を経由せずDBの実値を確認するため。 */
  public int likeCountOf(long postId) {
    return jdbc.sql("SELECT like_count FROM posts WHERE id = ?")
        .param(postId)
        .query(Integer.class)
        .single();
  }

  /** 投稿の {@code comment_count} を直接読む。 */
  public int commentCountOf(long postId) {
    return jdbc.sql("SELECT comment_count FROM posts WHERE id = ?")
        .param(postId)
        .query(Integer.class)
        .single();
  }
}
