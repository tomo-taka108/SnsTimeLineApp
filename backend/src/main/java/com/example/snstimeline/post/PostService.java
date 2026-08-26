package com.example.snstimeline.post;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.CursorCodec;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.post.dto.CreatePostRequest;
import com.example.snstimeline.post.dto.PostSummary;
import com.example.snstimeline.post.dto.UpdatePostRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 投稿・タイムラインの業務ロジック。トランザクション境界はこの層に置く（docs/07_architecture.md 2.1）。 */
@Service
public class PostService {

  /** ページ件数の上限（docs/05_api_design.md 2.1）。 */
  private static final int LIMIT_MAX = 50;

  private static final int LIMIT_DEFAULT = 20;

  private final PostMapper postMapper;
  private final LikeMapper likeMapper;

  public PostService(PostMapper postMapper, LikeMapper likeMapper) {
    this.postMapper = postMapper;
    this.likeMapper = likeMapper;
  }

  /**
   * #5 タイムライン取得（F-TL-01, F-TL-02, F-TL-03）。
   *
   * <p>タイムライン本体はJOIN1本、いいね判定は一括取得で1本の計2本 （docs/06_non_functional.md 1.3 の「3回以内」に対して余裕がある）。
   */
  @Transactional(readOnly = true)
  public CursorPage<PostSummary> getTimeline(
      Long meId, TimelineTab tab, Integer limitParam, String cursor) {
    int limit = clampLimit(limitParam);

    CursorCodec.Cursor decoded = cursor == null ? null : CursorCodec.decode(cursor);
    var cursorCreatedAt = decoded == null ? null : decoded.createdAt();
    var cursorId = decoded == null ? null : decoded.id();

    // limit + 1 件取得し、1件多く返ってきたら次ページがあると判定する
    // （COUNT(*) を別途投げずに済ませるため、docs/09_decision_log.md D-06）。
    List<PostRow> rows = postMapper.findTimeline(tab, meId, cursorCreatedAt, cursorId, limit + 1);

    boolean hasNext = rows.size() > limit;
    List<PostRow> page = hasNext ? rows.subList(0, limit) : rows;
    Set<Long> likedPostIds = likedPostIdsOf(meId, page.stream().map(PostRow::id).toList());
    List<PostSummary> items =
        page.stream().map(row -> PostSummary.from(row, likedPostIds.contains(row.id()))).toList();

    if (!hasNext || page.isEmpty()) {
      return CursorPage.last(items);
    }
    PostRow last = page.get(page.size() - 1);
    String nextCursor = CursorCodec.encode(last.createdAt(), last.id());
    return CursorPage.hasNext(items, nextCursor);
  }

  /**
   * 「自分がいいね済みの投稿ID」を一括取得する（docs/04_data_model.md 5.3、N+1回避）。
   *
   * <p>投稿1件ごとに問い合わせず、表示する投稿ID群に対して1回のクエリで済ませる。
   */
  private Set<Long> likedPostIdsOf(Long meId, List<Long> postIds) {
    if (postIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(likeMapper.findLikedPostIds(meId, postIds));
  }

  /**
   * #29 新着投稿の件数（設計書#1〜#28には無い、docs/09_decision_log.md D-31）。
   *
   * <p>SC-03 の新着通知バナー用。60秒ごとにポーリングされるため、COUNT(*) 1本だけで済ませ、 投稿本体は返さない。
   */
  @Transactional(readOnly = true)
  public long countNewer(Long meId, TimelineTab tab, Long sinceId) {
    return postMapper.countNewer(tab, meId, sinceId);
  }

  /** #6 投稿作成（F-PO-01）。 */
  @Transactional
  public PostSummary create(Long meId, CreatePostRequest request) {
    Long newId = postMapper.insert(meId, request.body());
    // created_at のサーバー値と author を正確に返すため、作成直後に1回引き直す。
    // 単発APIなのでSQL2本になるのは許容する。
    PostRow row =
        postMapper
            .findRowById(newId)
            .orElseThrow(() -> new IllegalStateException("投稿の作成直後に取得できませんでした。id=" + newId));
    // 作成直後の自分の投稿に、自分がいいね済みのはずがないため false 固定でよい
    return PostSummary.from(row, false);
  }

  /** #7 投稿詳細取得（F-PO-03）。 */
  @Transactional(readOnly = true)
  public PostSummary getById(Long meId, Long postId) {
    PostRow row = postMapper.findRowById(postId).orElseThrow(NotFoundException::new);
    boolean isLiked = likedPostIdsOf(meId, List.of(row.id())).contains(row.id());
    return PostSummary.from(row, isLiked);
  }

  /**
   * #8 投稿編集（F-PO-04）。
   *
   * <p>docs上はPhase2だが、今回MVPへ前倒しした（docs/09_decision_log.md D-30）。
   *
   * <p>認可は「① 存在チェック→404 → ② 所有者チェック→403」の順を必ず守る（D-14）。 {@code UPDATE ... WHERE id=? AND user_id=?}
   * の1文にまとめない。0件更新になっても 404と403のどちらなのか区別できなくなるため。
   */
  @Transactional
  public PostSummary update(Long meId, Long postId, UpdatePostRequest request) {
    Post post = postMapper.findById(postId).orElseThrow(NotFoundException::new);
    if (!post.userId().equals(meId)) {
      throw new ForbiddenException();
    }

    int affected = postMapper.updateBody(postId, request.body());
    if (affected == 0) {
      // ①②の判定後、更新までの間に削除された競合
      throw new NotFoundException();
    }

    PostRow row = postMapper.findRowById(postId).orElseThrow(NotFoundException::new);
    boolean isLiked = likedPostIdsOf(meId, List.of(row.id())).contains(row.id());
    return PostSummary.from(row, isLiked);
  }

  /** #9 投稿削除（F-PO-05）。認可の順序は update と同じ（D-14）。 */
  @Transactional
  public void delete(Long meId, Long postId) {
    Post post = postMapper.findById(postId).orElseThrow(NotFoundException::new);
    if (!post.userId().equals(meId)) {
      throw new ForbiddenException();
    }

    int affected = postMapper.softDelete(postId);
    if (affected == 0) {
      throw new NotFoundException();
    }
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
