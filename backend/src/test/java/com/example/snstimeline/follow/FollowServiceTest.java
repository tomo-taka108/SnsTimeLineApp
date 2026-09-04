package com.example.snstimeline.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.CursorCodec;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.follow.dto.FollowResponse;
import com.example.snstimeline.user.User;
import com.example.snstimeline.user.dto.UserListItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link FollowService} の単体テスト（docs/11_test_design.md 11章、ケース #100〜#120）。
 *
 * <p>{@code LikeService} と同じ冪等性の考え方に加え、本クラス独自の2点を確認する。
 *
 * <ul>
 *   <li>フォロー数は非正規化カウンタを持たず毎回 {@code COUNT(*)}（D-36）。{@code incrementFollowerCount} のようなメソッドは存在しない
 *   <li>{@code follow} と {@code unfollow} が非対称（自己チェックが {@code follow} にしか無い）
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  private static final long ME_ID = 5L;
  private static final long USER_ID = 100L;

  @Mock private com.example.snstimeline.user.UserMapper userMapper;
  @Mock private FollowMapper followMapper;

  @InjectMocks private FollowService followService;

  private void givenUserExists() {
    when(userMapper.findById(USER_ID))
        .thenReturn(
            Optional.of(
                new User(
                    USER_ID,
                    "user@example.com",
                    "hash",
                    "user1",
                    "表示名1",
                    null,
                    null,
                    null,
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    null,
                    null)));
  }

  private static FollowRow followRow(long followId, long userId) {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-01T12:00:00Z");
    return new FollowRow(followId, now, userId, "user" + userId, "表示名", null, null, now);
  }

  @Nested
  @DisplayName("フォローする")
  class Follow {

    @Test
    @DisplayName("#100 未フォローなら insert が呼ばれる")
    void 未フォローなら登録する() {
      givenUserExists();
      when(followMapper.exists(ME_ID, USER_ID)).thenReturn(false);
      when(followMapper.countFollowers(USER_ID)).thenReturn(1);

      FollowResponse response = followService.follow(ME_ID, USER_ID);

      verify(followMapper).insert(ME_ID, USER_ID);
      assertThat(response.isFollowing()).isTrue();
      assertThat(response.followerCount()).isEqualTo(1);
    }

    /**
     * #101 <b>17項目「フォローの冪等性」そのもの。</b>
     *
     * <p>事前SELECT方式（D-37）。ガードが外れると、連打や通信の再送のたびに INSERT が走り、UNIQUE制約違反（D-34と同じ理由で回復しない設計）か、
     * 制約が無ければ重複行が生まれる。
     */
    @Test
    @DisplayName("#101 フォロー済みなら insert を呼ばない（冪等）")
    void フォロー済みなら何もしない() {
      givenUserExists();
      when(followMapper.exists(ME_ID, USER_ID)).thenReturn(true);
      when(followMapper.countFollowers(USER_ID)).thenReturn(3);

      FollowResponse response = followService.follow(ME_ID, USER_ID);

      verify(followMapper, never()).insert(ME_ID, USER_ID);
      assertThat(response.isFollowing()).isTrue();
      assertThat(response.followerCount()).isEqualTo(3);
    }

    /** #102 {@code countFollowers} はガードの外側にあるため、未フォロー・フォロー済みどちらの経路でも呼ばれる。 */
    @Test
    @DisplayName("#102 フォロー済みの場合でも countFollowers は呼ばれる")
    void フォロー済みでも人数は取得する() {
      givenUserExists();
      when(followMapper.exists(ME_ID, USER_ID)).thenReturn(true);
      when(followMapper.countFollowers(USER_ID)).thenReturn(9);

      followService.follow(ME_ID, USER_ID);

      verify(followMapper).countFollowers(USER_ID);
    }

    /**
     * #105 <b>引数の取り違え検出。</b> {@code exists}/{@code insert} は共に {@code (followerId, followeeId)} の順で
     * {@code Long} 同士のため、逆に渡してもコンパイルは通る。
     */
    @Test
    @DisplayName("#105 mapper には (meId, userId) の順で渡す（引数が逆転していない）")
    void 引数の順序が正しい() {
      givenUserExists();
      when(followMapper.exists(ME_ID, USER_ID)).thenReturn(false);
      when(followMapper.countFollowers(USER_ID)).thenReturn(1);

      followService.follow(ME_ID, USER_ID);

      verify(followMapper).exists(ME_ID, USER_ID);
      verify(followMapper).insert(ME_ID, USER_ID);
    }
  }

  @Nested
  @DisplayName("フォローを外す")
  class Unfollow {

    @Test
    @DisplayName("#103 フォロー済みなら delete が呼ばれる")
    void フォロー済みなら解除する() {
      givenUserExists();
      when(followMapper.countFollowers(USER_ID)).thenReturn(0);

      FollowResponse response = followService.unfollow(ME_ID, USER_ID);

      verify(followMapper).delete(ME_ID, USER_ID);
      assertThat(response.isFollowing()).isFalse();
    }

    /** #104 17項目「フォロー解除の冪等性」そのもの。戻り値を見ずに delete するため、そもそも分岐が無い＝壊れにくい設計。 */
    @Test
    @DisplayName("#104 未フォローでも例外を投げず200を返す（冪等）")
    void 未フォローでも解除は成功する() {
      givenUserExists();
      when(followMapper.countFollowers(USER_ID)).thenReturn(0);

      FollowResponse response = followService.unfollow(ME_ID, USER_ID);

      assertThat(response.isFollowing()).isFalse();
      assertThat(response.followerCount()).isZero();
    }
  }

  @Nested
  @DisplayName("自己フォロー")
  class SelfFollow {

    @Test
    @DisplayName("#106 自分自身をフォローすると400。followMapper に一切触れない")
    void 自己フォローは拒否される() {
      assertThatThrownBy(() -> followService.follow(ME_ID, ME_ID))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);

      verifyNoInteractions(followMapper);
    }

    /**
     * #107 <b>本節の核心。</b> 自己チェックは存在チェックより<b>先</b>に行う（D-39）。もし後ろに来る実装へ変わると、 「存在しないユーザーIDへの自己フォロー」が
     * 400 ではなく 404 になる。 {@code userMapper} に一切触れないことまで確認し、存在確認自体が起きていないことを保証する。
     */
    @Test
    @DisplayName("#107 存在しないユーザーIDへの自己フォローでも400（404ではない）。userMapper に触れない")
    void 存在しないIDへの自己フォローも400() {
      assertThatThrownBy(() -> followService.follow(ME_ID, ME_ID))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);

      verifyNoInteractions(userMapper);
    }

    /** #108 {@code unfollow} には自己チェックが無い。自分自身の解除は通常どおり処理される（{@code follow} との非対称性）。 */
    @Test
    @DisplayName("#108 unfollowでは自分自身でも400にならず、delete が呼ばれる")
    void 自己解除は拒否されない() {
      when(userMapper.findById(ME_ID))
          .thenReturn(
              Optional.of(
                  new User(
                      ME_ID,
                      "me@example.com",
                      "hash",
                      "me",
                      "自分",
                      null,
                      null,
                      null,
                      OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                      null,
                      null)));
      when(followMapper.countFollowers(ME_ID)).thenReturn(0);

      followService.unfollow(ME_ID, ME_ID);

      verify(followMapper).delete(ME_ID, ME_ID);
    }
  }

  @Nested
  @DisplayName("存在チェック")
  class NotFound {

    @Test
    @DisplayName("#109 follow: 対象ユーザーが存在しなければ404。followMapper に触れない")
    void フォロー対象が存在しない() {
      when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> followService.follow(ME_ID, USER_ID))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(followMapper);
    }

    @Test
    @DisplayName("#110 unfollow: 対象ユーザーが存在しなければ404。followMapper に触れない")
    void 解除対象が存在しない() {
      when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> followService.unfollow(ME_ID, USER_ID))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(followMapper);
    }

    @Test
    @DisplayName("#111 getFollowing / getFollowers: 対象ユーザーが存在しなければ404")
    void 一覧取得対象が存在しない() {
      when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> followService.getFollowing(ME_ID, USER_ID, 20, null))
          .isInstanceOf(NotFoundException.class);
      assertThatThrownBy(() -> followService.getFollowers(ME_ID, USER_ID, 20, null))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("一覧取得")
  class Listing {

    /**
     * #112 <b>Issue #37 の対象。方針検討中のため現状の挙動を固定する。</b> {@code CommentService} と同じ「存在チェック→limit検証」の順。
     * {@code PostService} は逆順（limit検証→存在チェック）であり、この不整合自体が Issue #37。
     */
    @Test
    @DisplayName("#112 対象ユーザーなし＋limit不正が重なると404（400ではない。Issue #37 で方針検討中）")
    void 対象なしとlimit不正が重なると404が優先される() {
      when(userMapper.findById(USER_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> followService.getFollowing(ME_ID, USER_ID, 999, null))
          .isInstanceOf(NotFoundException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("#113 limit=0は400")
    void limitが0なら400() {
      givenUserExists();

      assertThatThrownBy(() -> followService.getFollowing(ME_ID, USER_ID, 0, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#115 limit=51は400")
    void limitが51なら400() {
      givenUserExists();

      assertThatThrownBy(() -> followService.getFollowing(ME_ID, USER_ID, 51, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#116 limit=nullなら既定値20で取得する")
    void limitがnullなら既定値() {
      givenUserExists();
      when(followMapper.findFollowing(USER_ID, null, null, 21)).thenReturn(List.of());

      followService.getFollowing(ME_ID, USER_ID, null, null);

      verify(followMapper).findFollowing(USER_ID, null, null, 21);
    }

    @Test
    @DisplayName("#117 取得件数がlimitちょうどならhasNext=false")
    void 件数がlimitちょうどなら次ページ無し() {
      givenUserExists();
      when(followMapper.findFollowing(USER_ID, null, null, 3))
          .thenReturn(List.of(followRow(1, 10), followRow(2, 11)));
      when(followMapper.findFollowedUserIds(eq(ME_ID), anyList())).thenReturn(List.of());

      CursorPage<UserListItem> page = followService.getFollowing(ME_ID, USER_ID, 2, null);

      assertThat(page.hasNext()).isFalse();
      assertThat(page.nextCursor()).isNull();
      assertThat(page.items()).hasSize(2);
    }

    @Test
    @DisplayName("#118 取得件数がlimit+1ならhasNext=true、先頭limit件のみ返す")
    void 件数がlimit超過なら次ページ有り() {
      givenUserExists();
      when(followMapper.findFollowing(USER_ID, null, null, 3))
          .thenReturn(List.of(followRow(1, 10), followRow(2, 11), followRow(3, 12)));
      when(followMapper.findFollowedUserIds(eq(ME_ID), anyList())).thenReturn(List.of());

      CursorPage<UserListItem> page = followService.getFollowing(ME_ID, USER_ID, 2, null);

      assertThat(page.hasNext()).isTrue();
      assertThat(page.items()).hasSize(2);
      assertThat(page.nextCursor()).isNotNull();
    }

    /** #119 17項目のN+1回避（04 の 6.6、D-38）に対応。空ページに対して無駄なフォロー判定クエリを飛ばさない。 */
    @Test
    @DisplayName("#119 ページが空ならfindFollowedUserIdsを呼ばない（N+1回避）")
    void 空ページならフォロー判定を呼ばない() {
      givenUserExists();
      when(followMapper.findFollowing(USER_ID, null, null, 21)).thenReturn(List.of());

      followService.getFollowing(ME_ID, USER_ID, null, null);

      verify(followMapper, never())
          .findFollowedUserIds(org.mockito.ArgumentMatchers.anyLong(), anyList());
    }

    /**
     * #120 <b>nextCursorの元になる値の取り違え検出。</b> {@code followCreatedAt}/{@code
     * followId}（フォロー行のキー）を使う必要があり、 {@code userCreatedAt}/{@code
     * userId}（ユーザー行のキー）を使うと、フォロー日時ではなくユーザー登録日時でページングされてしまう。
     */
    @Test
    @DisplayName("#120 nextCursorはfollowCreatedAt/followIdから作られる")
    void カーソルはフォロー行の値から作られる() {
      givenUserExists();
      OffsetDateTime followCreatedAt = OffsetDateTime.parse("2026-05-01T00:00:00Z");
      OffsetDateTime userCreatedAt = OffsetDateTime.parse("2020-01-01T00:00:00Z");
      FollowRow row1 =
          new FollowRow(1L, followCreatedAt, 10L, "user10", "表示名", null, null, userCreatedAt);
      FollowRow row2 =
          new FollowRow(2L, followCreatedAt, 11L, "user11", "表示名", null, null, userCreatedAt);
      when(followMapper.findFollowing(USER_ID, null, null, 2)).thenReturn(List.of(row1, row2));
      when(followMapper.findFollowedUserIds(eq(ME_ID), anyList())).thenReturn(List.of());

      CursorPage<UserListItem> page = followService.getFollowing(ME_ID, USER_ID, 1, null);

      String expected = CursorCodec.encode(followCreatedAt, 1L);
      assertThat(page.nextCursor()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("順序の確認")
  class Ordering {

    @Test
    @DisplayName("follow: exists → insert の順で呼ばれる")
    void 確認してから登録する() {
      givenUserExists();
      when(followMapper.exists(ME_ID, USER_ID)).thenReturn(false);
      when(followMapper.countFollowers(USER_ID)).thenReturn(1);

      followService.follow(ME_ID, USER_ID);

      InOrder inOrder = inOrder(followMapper);
      inOrder.verify(followMapper).exists(ME_ID, USER_ID);
      inOrder.verify(followMapper).insert(ME_ID, USER_ID);
    }
  }
}
