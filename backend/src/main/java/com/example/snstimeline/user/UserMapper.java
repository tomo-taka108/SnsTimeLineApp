package com.example.snstimeline.user;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * users テーブルへのアクセス。SQLは resources/mapper/UserMapper.xml に集約する。
 *
 * <p><b>論理削除の扱い（docs/09_decision_log.md D-25）</b>: MyBatis には Hibernate の {@code @SQLRestriction}
 * に相当する自動除外が無いため、各SQLに {@code deleted_at IS NULL} を明示的に書く。 論理削除を無視するメソッドには名前に {@code
 * IncludingDeleted} を付け、逸脱が名前で分かるようにする。
 */
@Mapper
public interface UserMapper {

  /** ログイン用。論理削除済みユーザーは取得しない。 */
  Optional<User> findByEmail(@Param("email") String email);

  /** /auth/me 用。論理削除済みユーザーは取得しない。 */
  Optional<User> findById(@Param("id") Long id);

  /**
   * 登録済みメールアドレスかどうか。
   *
   * <p><b>意図的に論理削除を無視する。</b> 一意制約が deleted_at を絡めない設計＝退会したメールは 再利用させない方針のため、退会済みユーザーも重複として扱うのが正しい。
   */
  boolean existsByEmailIncludingDeleted(@Param("email") String email);

  /** 同上（ユーザー名）。 */
  boolean existsByUsernameIncludingDeleted(@Param("username") String username);

  /**
   * ユーザーを1件登録し、採番された id を返す。
   *
   * <p>User が record（不変）でありキーを書き戻せないため、{@code RETURNING id} で受け取る。
   */
  Long insert(@Param("user") User user);
}
