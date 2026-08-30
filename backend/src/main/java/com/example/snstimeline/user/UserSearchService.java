package com.example.snstimeline.user;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.OffsetPage;
import com.example.snstimeline.common.ValidationConstants;
import com.example.snstimeline.follow.FollowMapper;
import com.example.snstimeline.user.dto.UserListItem;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ユーザー検索の業務ロジック（docs/05_api_design.md #20 / F-US-05 / SC-07）。
 *
 * <p>{@code UserService}（プロフィールの取得・編集）とは分離する（{@code LikeService} と {@code PostService} を
 * 分けたのと同じ理由、単一責任）。検索はバリデーション・エスケープ・ページング・フォロー判定と 関心事が多く、プロフィールCRUDとは変更理由が異なる。
 *
 * <p><b>本アプリで唯一オフセットページネーションを使う。</b> 検索結果は並びが安定しており「3ページ目に飛ぶ」操作が自然なため（docs/05_api_design.md 2.2）。
 * 新着が絶えず挿入されるタイムラインとは事情が違う。
 */
@Service
public class UserSearchService {

  private final UserMapper userMapper;
  private final FollowMapper followMapper;

  public UserSearchService(UserMapper userMapper, FollowMapper followMapper) {
    this.userMapper = userMapper;
    this.followMapper = followMapper;
  }

  /**
   * #20 ユーザー検索（F-US-05）。
   *
   * <p>バリデーションを Bean Validation ではなくここで行う理由: 本プロジェクトには {@code @Validated} を付けたコントローラも {@code
   * ConstraintViolationException} のハンドラも無く、 クエリパラメータの検証は Service 層で行うのが既存の作法のため（{@code
   * FollowService.clampLimit} と同じ）。
   *
   * @param q 検索キーワード。1〜50コードポイント
   * @param pageParam 0始まりのページ番号。null なら 0
   * @param sizeParam 1ページあたり件数。null なら 20、最大 50
   */
  @Transactional(readOnly = true)
  public OffsetPage<UserListItem> search(
      Long meId, String q, Integer pageParam, Integer sizeParam) {
    String keyword = validateQuery(q);
    int page = validatePage(pageParam);
    int size = validateSize(sizeParam);

    // LIKE のメタ文字を無効化した値と、pg_trgm の % 演算子に渡す生の値を作り分ける。
    // エスケープ済みの値を % 演算子に渡すとバックスラッシュ自体が比較対象になり一致しなくなる。
    String qEscaped = escapeLikePattern(keyword);

    long totalElements = userMapper.countSearchUsers(qEscaped, keyword, meId);
    if (totalElements == 0) {
      return OffsetPage.empty(page, size);
    }

    List<UserSearchRow> rows = userMapper.searchUsers(qEscaped, keyword, meId, size, page * size);

    // isFollowing の一括取得（docs/04_data_model.md 6.6、N+1回避）。
    // 検索1回につきフォロー判定のクエリは1回だけにすること。
    // isLikedByMe（PostService.likedPostIdsOf）/ FollowService.toPage とは対象テーブルが
    // 違うため、3箇所目でも共通ヘルパーには切り出さない（docs/09_decision_log.md D-38, D-49）。
    List<Long> userIds = rows.stream().map(UserSearchRow::userId).toList();
    Set<Long> followedIds =
        userIds.isEmpty()
            ? Set.of()
            : new HashSet<>(followMapper.findFollowedUserIds(meId, userIds));

    // isMe は常に false。検索結果から自分自身を除いているため（SQL の id <> meId）。
    List<UserListItem> items =
        rows.stream()
            .map(row -> UserListItem.fromSearchRow(row, followedIds.contains(row.userId())))
            .toList();

    return OffsetPage.of(items, page, size, totalElements);
  }

  /**
   * キーワードの検証。トリムした値を返す。
   *
   * <p>文字数は {@code String.length()}（UTF-16単位）ではなくコードポイント数で数える。 絵文字を2文字と数えてしまい、PostgreSQL の {@code
   * char_length} と食い違うため （{@code CodePointLengthValidator} と同じ数え方）。
   */
  private static String validateQuery(String q) {
    if (q == null) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    String trimmed = q.trim();
    int length = trimmed.codePointCount(0, trimmed.length());
    if (length < ValidationConstants.SEARCH_QUERY_MIN
        || length > ValidationConstants.SEARCH_QUERY_MAX) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    return trimmed;
  }

  private static int validatePage(Integer pageParam) {
    if (pageParam == null) {
      return 0;
    }
    if (pageParam < 0) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    return pageParam;
  }

  private static int validateSize(Integer sizeParam) {
    if (sizeParam == null) {
      return ValidationConstants.SEARCH_SIZE_DEFAULT;
    }
    if (sizeParam < 1 || sizeParam > ValidationConstants.SEARCH_SIZE_MAX) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    return sizeParam;
  }

  /**
   * LIKE のメタ文字を無効化する（docs/04_data_model.md 6.5）。SQL側の {@code ESCAPE '\'} とセットで使う。
   *
   * <p><b>{@code %} のエスケープを忘れると {@code q=%} で全ユーザーが列挙される。</b>
   * これはSQLインジェクションではない（パラメータバインディングは効いている）が、 情報漏洩としては同等に危険であり、<b>パラメータバインディングだけでは防げない</b>。
   *
   * <p>バックスラッシュを最初に置換すること。後にすると、{@code %} を {@code \%} に変換した その {@code \} 自体を二重化してしまう。
   */
  private static String escapeLikePattern(String input) {
    return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
