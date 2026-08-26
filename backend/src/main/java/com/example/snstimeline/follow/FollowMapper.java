package com.example.snstimeline.follow;

import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code follows} テーブルへのアクセス（docs/09_decision_log.md D-25）。
 *
 * <p>{@code follows} は物理削除（{@code deleted_at} が無い、docs/09_decision_log.md D-02）。 users
 * を参照する一覧系メソッドは、退会済みユーザーを除くため {@code u.deleted_at IS NULL} を JOIN条件に含める（{@code
 * UserMapper.activeOnly} と同じ考え方だが、テーブルが違うので include は共有しない）。
 */
@Mapper
public interface FollowMapper {

  /**
   * #21 フォロー登録。
   *
   * <p>呼び出し前に {@link #exists} で存在確認し、TOCTOU（確認後の競合）はDBのUNIQUE制約
   * （uq_follows_follower_followee）に委ねる。PostgreSQLは制約違反が起きたトランザクションを
   * 中断状態にし、Java側で例外を捕捉しても同一トランザクション内の以降の文がすべて失敗するため （#21 は挿入後に followerCount
   * を数える＝いいねとまったく同じ罠）、事前確認で重複を避ける （docs/09_decision_log.md D-34, D-37）。
   */
  int insert(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

  /** 既にフォロー済みかどうか（冪等性の判定用）。 */
  boolean exists(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

  /** #22 フォロー解除。物理削除（docs/09_decision_log.md D-02）。 */
  int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

  /** フォロワー数（自分がフォローされている数）。非正規化カウンタを持たず都度算出する（D-36）。 */
  int countFollowers(@Param("userId") Long userId);

  /** フォロー中数（自分がフォローしている数）。 */
  int countFollowing(@Param("userId") Long userId);

  /**
   * #23 フォロー中一覧。フォローした新しい順（{@code follows.created_at, follows.id} の降順）。
   *
   * <p>{@code limit} には「取得したい件数 + 1」を渡すこと（{@code PostMapper.findTimeline} と同じ規約）。
   */
  List<FollowRow> findFollowing(
      @Param("userId") Long userId,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** #24 フォロワー一覧。フォローされた新しい順。 */
  List<FollowRow> findFollowers(
      @Param("userId") Long userId,
      @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /**
   * 自分がフォロー済みのユーザーIDを一括取得する（docs/04_data_model.md 6.6、N+1回避）。
   *
   * <p>#23 / #24 の一覧表示、プロフィールの複数箇所で使う想定。ユーザー1件ごとに問い合わせず、 表示するユーザーID群に対して1回のクエリで済ませること。
   *
   * @param userIds 空リストの場合は呼び出さないこと（Service側でガードする）
   */
  List<Long> findFollowedUserIds(@Param("meId") Long meId, @Param("userIds") List<Long> userIds);
}
