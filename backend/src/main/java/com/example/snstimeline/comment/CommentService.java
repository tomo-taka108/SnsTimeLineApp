package com.example.snstimeline.comment;

import com.example.snstimeline.comment.dto.CommentSummary;
import com.example.snstimeline.comment.dto.CreateCommentRequest;
import com.example.snstimeline.comment.dto.CreateCommentResponse;
import com.example.snstimeline.comment.dto.DeleteCommentResponse;
import com.example.snstimeline.comment.dto.UpdateCommentRequest;
import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.CursorCodec;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.post.PostMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** コメントの業務ロジック。トランザクション境界はこの層に置く（docs/07_architecture.md 2.1、PostServiceと同じ方針）。 */
@Service
public class CommentService {

  /** ページ件数の上限（docs/05_api_design.md 2.1）。PostServiceと同値だが、3箇所目が出るまでは重複を許容する（D-35）。 */
  private static final int LIMIT_MAX = 50;

  private static final int LIMIT_DEFAULT = 20;

  private final CommentMapper commentMapper;
  private final PostMapper postMapper;

  public CommentService(CommentMapper commentMapper, PostMapper postMapper) {
    this.commentMapper = commentMapper;
    this.postMapper = postMapper;
  }

  /**
   * #10 コメント一覧取得（F-CM-02）。
   *
   * <p>投稿自体が存在しない・論理削除済みなら404（削除済み投稿のコメント欄は出さない）。
   */
  @Transactional(readOnly = true)
  public CursorPage<CommentSummary> getComments(
      Long meId, Long postId, Integer limitParam, String cursor) {
    postMapper.findById(postId).orElseThrow(NotFoundException::new);

    int limit = clampLimit(limitParam);
    CursorCodec.Cursor decoded = cursor == null ? null : CursorCodec.decode(cursor);
    var cursorCreatedAt = decoded == null ? null : decoded.createdAt();
    var cursorId = decoded == null ? null : decoded.id();

    List<CommentRow> rows =
        commentMapper.findByPostId(postId, cursorCreatedAt, cursorId, limit + 1);

    boolean hasNext = rows.size() > limit;
    List<CommentRow> page = hasNext ? rows.subList(0, limit) : rows;
    List<CommentSummary> items = page.stream().map(row -> CommentSummary.from(row, meId)).toList();

    if (!hasNext || page.isEmpty()) {
      return CursorPage.last(items);
    }
    CommentRow last = page.get(page.size() - 1);
    String nextCursor = CursorCodec.encode(last.createdAt(), last.id());
    return CursorPage.hasNext(items, nextCursor);
  }

  /** #11 コメント投稿（F-CM-01）。同一トランザクションで posts.comment_count を +1（D-01）。 */
  @Transactional
  public CreateCommentResponse create(Long meId, Long postId, CreateCommentRequest request) {
    postMapper.findById(postId).orElseThrow(NotFoundException::new);

    Long newId = commentMapper.insert(postId, meId, request.body());
    postMapper.incrementCommentCount(postId);

    CommentRow row =
        commentMapper
            .findRowById(newId)
            .orElseThrow(() -> new IllegalStateException("コメントの作成直後に取得できませんでした。id=" + newId));
    int commentCount = postMapper.findCommentCount(postId);
    return new CreateCommentResponse(CommentSummary.from(row, meId), commentCount);
  }

  /**
   * #13 コメント削除（F-CM-04）。
   *
   * <p>認可は「① 存在チェック→404 → ② 所有者チェック→403」の順を必ず守る（D-14）。 同一トランザクションで posts.comment_count を -1。
   */
  @Transactional
  public DeleteCommentResponse delete(Long meId, Long commentId) {
    Comment comment = commentMapper.findById(commentId).orElseThrow(NotFoundException::new);
    if (!comment.userId().equals(meId)) {
      throw new ForbiddenException();
    }

    int affected = commentMapper.softDelete(commentId);
    if (affected == 0) {
      // ①②の判定後、削除までの間に既に削除された競合
      throw new NotFoundException();
    }

    postMapper.decrementCommentCount(comment.postId());
    int commentCount = postMapper.findCommentCount(comment.postId());
    return new DeleteCommentResponse(commentCount);
  }

  /**
   * #12 コメント編集（F-CM-03、docs/09_decision_log.md D-51 によりMVPへ前倒し）。
   *
   * <p>認可は「① 存在チェック→404 → ② 所有者チェック→403」の順を必ず守る（D-14）。 comment_count は変わらないため触らない（D-01 の影響範囲に #12
   * は含まれない）。
   */
  @Transactional
  public CommentSummary update(Long meId, Long commentId, UpdateCommentRequest request) {
    Comment comment = commentMapper.findById(commentId).orElseThrow(NotFoundException::new);
    if (!comment.userId().equals(meId)) {
      throw new ForbiddenException();
    }

    int affected = commentMapper.updateBody(commentId, request.body());
    if (affected == 0) {
      // ①②の判定後、更新までの間に既に削除された競合
      throw new NotFoundException();
    }

    CommentRow row = commentMapper.findRowById(commentId).orElseThrow(NotFoundException::new);
    return CommentSummary.from(row, meId);
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
