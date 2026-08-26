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

  /** /auth/me 用。論理削除済みユーザーは取得しない。#17 プロフィール取得でも使う。 */
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

  /**
   * #18 プロフィールの投稿数（docs/05_api_design.md UserProfile.postCount）。
   *
   * <p>非正規化カウンタは持たず都度 {@code COUNT(*)} で算出する（docs/09_decision_log.md D-36）。 {@code
   * idx_posts_user_created} で索引だけで数えられる。
   */
  int countPosts(@Param("userId") Long userId);

  /**
   * #19 プロフィール編集。送られたフィールドのみ更新する。
   *
   * @param displayName null なら変更しない
   * @param bioProvided true なら bio カラムを更新する（bio 自体が null でも「削除」として書き込む）
   * @param bio bioProvided が false のときは無視される
   */
  int updateProfile(
      @Param("userId") Long userId,
      @Param("displayName") String displayName,
      @Param("bioProvided") boolean bioProvided,
      @Param("bio") String bio);
}
