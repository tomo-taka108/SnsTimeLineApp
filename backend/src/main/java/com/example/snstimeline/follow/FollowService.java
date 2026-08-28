package com.example.snstimeline.follow;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.CursorCodec;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.follow.dto.FollowResponse;
import com.example.snstimeline.user.UserMapper;
import com.example.snstimeline.user.dto.UserListItem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * フォローの業務ロジック（docs/05_api_design.md #21〜#24）。
 *
 * <p>{@code UserService} とは分離する（{@code LikeService} と {@code PostService} を分けたのと同じ理由、単一責任）。
 *
 * <p>フォローには「所有者チェック」に相当する概念が無い（誰でも他人をフォローできる）。 D-14の2段階認可（存在チェック→404、所有者チェック→403）はそのまま適用できない。
 * 代わりに「自分自身か」という別の判定（400 SELF_FOLLOW_NOT_ALLOWED）が入る。
 */
@Service
public class FollowService {

  private static final int LIMIT_MAX = 50;
  private static final int LIMIT_DEFAULT = 20;

  private final UserMapper userMapper;
  private final FollowMapper followMapper;

  public FollowService(UserMapper userMapper, FollowMapper followMapper) {
    this.userMapper = userMapper;
    this.followMapper = followMapper;
  }

  /**
   * #21 フォロー（F-FL-01）。冪等: 既にフォロー済みでも {@code 200 OK} を返す。
   *
   * <p>自己フォローの判定を存在チェックより先に行う。D-14の「存在→404、所有者→403」は 他人のリソースを操作する場合の順序であり、これは「自分自身か」という別の判定のため、
   * 先に400を返しても情報は漏れない （docs/09_decision_log.md D-39 で自己フォロー判定の位置づけを記録）。
   *
   * <p>事前に {@link FollowMapper#exists} で確認してから INSERT する（docs/09_decision_log.md D-37）。
   * PostgreSQLは制約違反が起きたトランザクションを「中断状態」にし、Java側で例外を捕まえても 同じトランザクション内の以降の文（ここでは followerCount
   * の再取得）がすべて失敗するため、 UNIQUE制約違反を実行時に捕捉して回復する設計は取らない（D-34と同じ）。
   */
  @Transactional
  public FollowResponse follow(Long meId, Long userId) {
    if (meId.equals(userId)) {
      throw new ApiException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
    }
    userMapper.findById(userId).orElseThrow(NotFoundException::new);

    if (!followMapper.exists(meId, userId)) {
      followMapper.insert(meId, userId);
    }
    return new FollowResponse(true, followMapper.countFollowers(userId));
  }

  /**
   * #22 フォロー解除（F-FL-02）。冪等: フォローしていない状態で呼ばれても {@code 200 OK}。
   *
   * <p>存在しないユーザーへの解除も404にする（#22 にエラーコードの明記は無いが、#21 と同じ挙動に揃える）。
   */
  @Transactional
  public FollowResponse unfollow(Long meId, Long userId) {
    userMapper.findById(userId).orElseThrow(NotFoundException::new);

    followMapper.delete(meId, userId);
    return new FollowResponse(false, followMapper.countFollowers(userId));
  }

  /** #23 フォロー中一覧（F-FL-03）。フォローした新しい順。 */
  @Transactional(readOnly = true)
  public CursorPage<UserListItem> getFollowing(
      Long meId, Long userId, Integer limitParam, String cursor) {
    userMapper.findById(userId).orElseThrow(NotFoundException::new);
    int limit = clampLimit(limitParam);
    CursorCodec.Cursor decoded = cursor == null ? null : CursorCodec.decode(cursor);
    List<FollowRow> rows =
        followMapper.findFollowing(
            userId,
            decoded == null ? null : decoded.createdAt(),
            decoded == null ? null : decoded.id(),
            limit + 1);
    return toPage(meId, rows, limit);
  }

  /** #24 フォロワー一覧（F-FL-04）。フォローされた新しい順。 */
  @Transactional(readOnly = true)
  public CursorPage<UserListItem> getFollowers(
      Long meId, Long userId, Integer limitParam, String cursor) {
    userMapper.findById(userId).orElseThrow(NotFoundException::new);
    int limit = clampLimit(limitParam);
    CursorCodec.Cursor decoded = cursor == null ? null : CursorCodec.decode(cursor);
    List<FollowRow> rows =
        followMapper.findFollowers(
            userId,
            decoded == null ? null : decoded.createdAt(),
            decoded == null ? null : decoded.id(),
            limit + 1);
    return toPage(meId, rows, limit);
  }

  private CursorPage<UserListItem> toPage(Long meId, List<FollowRow> rows, int limit) {
    boolean hasNext = rows.size() > limit;
    List<FollowRow> page = hasNext ? rows.subList(0, limit) : rows;

    // isFollowing の一括取得（docs/04_data_model.md 6.6、N+1回避）。
    // isLikedByMe（PostService.likedPostIdsOf）とは対象が違うため共通ヘルパーには
    // 切り出さない（docs/09_decision_log.md D-38）。
    List<Long> userIds = page.stream().map(FollowRow::userId).toList();
    Set<Long> followedIds =
        userIds.isEmpty()
            ? Set.of()
            : new HashSet<>(followMapper.findFollowedUserIds(meId, userIds));

    List<UserListItem> items =
        page.stream()
            .map(
                row ->
                    UserListItem.fromFollowRow(
                        row, followedIds.contains(row.userId()), row.userId().equals(meId)))
            .toList();

    if (!hasNext || page.isEmpty()) {
      return CursorPage.last(items);
    }
    FollowRow last = page.get(page.size() - 1);
    String nextCursor = CursorCodec.encode(last.followCreatedAt(), last.followId());
    return CursorPage.hasNext(items, nextCursor);
  }

  private static int clampLimit(Integer limitParam) {
    if (limitParam == null) {
      return LIMIT_DEFAULT;
    }
    if (limitParam < 1 || limitParam > LIMIT_MAX) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    return limitParam;
  }
}
