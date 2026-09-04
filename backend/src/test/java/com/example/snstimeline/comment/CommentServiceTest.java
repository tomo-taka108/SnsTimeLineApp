package com.example.snstimeline.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.snstimeline.comment.dto.CreateCommentRequest;
import com.example.snstimeline.comment.dto.CreateCommentResponse;
import com.example.snstimeline.comment.dto.DeleteCommentResponse;
import com.example.snstimeline.comment.dto.UpdateCommentRequest;
import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.post.Post;
import com.example.snstimeline.post.PostMapper;
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
 * {@link CommentService} の単体テスト（docs/11_test_design.md 9章、ケース #84〜#99）。
 *
 * <p>06_non_functional.md 5.3 の③④は対になったカウンタの非対称ルール——コメント削除は {@code comment_count}
 * を-1するが、投稿削除・コメント編集では変えない。{@link com.example.snstimeline.post.PostServiceTest} の④とあわせて完成する。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

  private static final long ME_ID = 5L;
  private static final long OTHER_USER_ID = 999L;
  private static final long POST_ID = 100L;
  private static final long COMMENT_ID = 200L;

  @Mock private CommentMapper commentMapper;
  @Mock private PostMapper postMapper;

  @InjectMocks private CommentService commentService;

  /** 自分が投稿者である {@link Comment} を返すよう設定する。{@code postId} はカウンタ対象の確認に使う。 */
  private void givenOwnCommentExists() {
    when(commentMapper.findById(COMMENT_ID))
        .thenReturn(Optional.of(comment(COMMENT_ID, POST_ID, ME_ID)));
  }

  private void givenOtherUsersCommentExists() {
    when(commentMapper.findById(COMMENT_ID))
        .thenReturn(Optional.of(comment(COMMENT_ID, POST_ID, OTHER_USER_ID)));
  }

  private static Comment comment(long id, long postId, long userId) {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-01T12:00:00Z");
    return new Comment(id, postId, userId, "コメント本文", now, now, null, null);
  }

  /**
   * {@link CommentRow} は8項目でファクトリが無い。{@code authorId} が null だと {@code CommentSummary.from}
   * でNPEになる。
   */
  private static CommentRow commentRow(long id, long authorId) {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-01T12:00:00Z");
    return new CommentRow(id, "コメント本文", now, null, authorId, "user1", "表示名1", null);
  }

  @Nested
  @DisplayName("コメントの削除")
  class Delete {

    @Test
    @DisplayName("#84 コメントが無ければ404。softDeleteを呼ばない")
    void コメントがなければ404() {
      when(commentMapper.findById(COMMENT_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> commentService.delete(ME_ID, COMMENT_ID))
          .isInstanceOf(NotFoundException.class);

      verify(commentMapper, never()).softDelete(COMMENT_ID);
    }

    @Test
    @DisplayName("#85 他人のコメントなら403。softDeleteを呼ばない")
    void 他人のコメントは削除できない() {
      givenOtherUsersCommentExists();

      assertThatThrownBy(() -> commentService.delete(ME_ID, COMMENT_ID))
          .isInstanceOf(ForbiddenException.class);

      verify(commentMapper, never()).softDelete(COMMENT_ID);
    }

    /** #86 <b>06_non_functional.md 5.3 ③「コメントを論理削除するとcomment_countが-1される」そのもの。</b> */
    @Test
    @DisplayName("#86 自分のコメントならdecrementCommentCountが1回呼ばれる")
    void 削除するとカウンタが減る() {
      givenOwnCommentExists();
      when(commentMapper.softDelete(COMMENT_ID)).thenReturn(1);
      when(postMapper.findCommentCount(POST_ID)).thenReturn(2);

      commentService.delete(ME_ID, COMMENT_ID);

      verify(postMapper).decrementCommentCount(POST_ID);
    }

    /**
     * #87 <b>D-14③の競合分岐。特に重要。</b>
     *
     * <p>削除できていないのにカウンタだけ減ると、実際のコメント数と comment_count が ずれ、以後ずっと不整合が残る。
     */
    @Test
    @DisplayName("#87 削除件数0（競合）なら404で、カウンタを減らさない")
    void 削除件数0ならカウンタも減らない() {
      givenOwnCommentExists();
      when(commentMapper.softDelete(COMMENT_ID)).thenReturn(0);

      assertThatThrownBy(() -> commentService.delete(ME_ID, COMMENT_ID))
          .isInstanceOf(NotFoundException.class);

      verify(postMapper, never()).decrementCommentCount(POST_ID);
    }

    /**
     * #88 <b>減らす対象は引数の {@code commentId} ではなく、取得したコメントが属する {@code postId}。</b>
     *
     * <p>ここを取り違えると、まったく無関係な投稿のコメント数が減る。{@code commentId} と {@code postId} は どちらも {@code Long}
     * のため、取り違えてもコンパイルは通る。
     */
    @Test
    @DisplayName("#88 カウンタ対象は comment.postId()（コメントIDではない）")
    void カウンタ対象は投稿ID() {
      long otherPostId = 777L;
      when(commentMapper.findById(COMMENT_ID))
          .thenReturn(Optional.of(comment(COMMENT_ID, otherPostId, ME_ID)));
      when(commentMapper.softDelete(COMMENT_ID)).thenReturn(1);
      when(postMapper.findCommentCount(otherPostId)).thenReturn(0);

      commentService.delete(ME_ID, COMMENT_ID);

      verify(postMapper).decrementCommentCount(otherPostId);
      verify(postMapper, never()).decrementCommentCount(COMMENT_ID);
    }

    @Test
    @DisplayName("#89 削除後のコメント数を返す")
    void 削除後の件数を返す() {
      givenOwnCommentExists();
      when(commentMapper.softDelete(COMMENT_ID)).thenReturn(1);
      when(postMapper.findCommentCount(POST_ID)).thenReturn(4);

      DeleteCommentResponse response = commentService.delete(ME_ID, COMMENT_ID);

      assertThat(response.commentCount()).isEqualTo(4);
    }
  }

  @Nested
  @DisplayName("コメントの更新")
  class Update {

    private final UpdateCommentRequest request = new UpdateCommentRequest("修正後のコメント");

    @Test
    @DisplayName("#90 コメントが無ければ404。updateBodyを呼ばない")
    void コメントがなければ404() {
      when(commentMapper.findById(COMMENT_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> commentService.update(ME_ID, COMMENT_ID, request))
          .isInstanceOf(NotFoundException.class);

      verify(commentMapper, never()).updateBody(eq(COMMENT_ID), anyString());
    }

    @Test
    @DisplayName("#91 他人のコメントなら403。updateBodyを呼ばない")
    void 他人のコメントは更新できない() {
      givenOtherUsersCommentExists();

      assertThatThrownBy(() -> commentService.update(ME_ID, COMMENT_ID, request))
          .isInstanceOf(ForbiddenException.class);

      verify(commentMapper, never()).updateBody(eq(COMMENT_ID), anyString());
    }

    /**
     * #92 <b>非対称ルールの一部。</b> 本文を直すだけなので件数は変わらない。 {@code verifyNoInteractions} で「一度も触っていない」ことまで確かめる。
     */
    @Test
    @DisplayName("#92 自分のコメントを更新しても postMapper に一切触れない")
    void 更新ではカウンタに触れない() {
      givenOwnCommentExists();
      when(commentMapper.updateBody(COMMENT_ID, "修正後のコメント")).thenReturn(1);
      when(commentMapper.findRowById(COMMENT_ID))
          .thenReturn(Optional.of(commentRow(COMMENT_ID, ME_ID)));

      commentService.update(ME_ID, COMMENT_ID, request);

      verifyNoInteractions(postMapper);
    }

    @Test
    @DisplayName("#93 更新件数0（競合）なら404")
    void 更新件数0なら404() {
      givenOwnCommentExists();
      when(commentMapper.updateBody(COMMENT_ID, "修正後のコメント")).thenReturn(0);

      assertThatThrownBy(() -> commentService.update(ME_ID, COMMENT_ID, request))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("コメントの作成")
  class Create {

    private final CreateCommentRequest request = new CreateCommentRequest("コメント本文");

    @Test
    @DisplayName("#94 投稿が無ければ404。commentMapperに一切触れない")
    void 投稿がなければ404() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> commentService.create(ME_ID, POST_ID, request))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(commentMapper);
    }

    @Test
    @DisplayName("#95 insert → incrementCommentCount の順で呼ばれる")
    void 登録してからカウンタを増やす() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.of(Post.forCreate(ME_ID, "投稿本文")));
      when(commentMapper.insert(POST_ID, ME_ID, "コメント本文")).thenReturn(COMMENT_ID);
      when(commentMapper.findRowById(COMMENT_ID))
          .thenReturn(Optional.of(commentRow(COMMENT_ID, ME_ID)));
      when(postMapper.findCommentCount(POST_ID)).thenReturn(1);

      commentService.create(ME_ID, POST_ID, request);

      InOrder inOrder = inOrder(commentMapper, postMapper);
      inOrder.verify(commentMapper).insert(POST_ID, ME_ID, "コメント本文");
      inOrder.verify(postMapper).incrementCommentCount(POST_ID);
    }

    @Test
    @DisplayName("#96 作成後のコメント数を返す")
    void 作成後の件数を返す() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.of(Post.forCreate(ME_ID, "投稿本文")));
      when(commentMapper.insert(POST_ID, ME_ID, "コメント本文")).thenReturn(COMMENT_ID);
      when(commentMapper.findRowById(COMMENT_ID))
          .thenReturn(Optional.of(commentRow(COMMENT_ID, ME_ID)));
      when(postMapper.findCommentCount(POST_ID)).thenReturn(3);

      CreateCommentResponse response = commentService.create(ME_ID, POST_ID, request);

      assertThat(response.commentCount()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("一覧取得")
  class GetComments {

    @Test
    @DisplayName("#97 投稿が無ければ404。commentMapperに触れない")
    void 投稿がなければ404() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> commentService.getComments(ME_ID, POST_ID, 20, null))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(commentMapper);
    }

    /**
     * #98 <b>PostServiceTest #80 との差。あちらは同じ状況で400を返す。</b>
     *
     * <p>CommentService は存在チェックが clampLimit より先のため、「存在しない投稿＋不正なlimit」では 400ではなく404になる。どちらが正しいかは
     * Issue #37 で方針検討中のため、<b>現時点の挙動をそのまま記録</b>する。 方針が決まったらこのテストごと変更する。
     */
    @Test
    @DisplayName("#98 投稿が無い＋limitも不正なら404（400ではない。Issue #37 で方針検討中）")
    void 投稿なしとlimit不正が重なると404が優先される() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      // NotFoundException も ApiException のサブクラスだが、エラーコードは VALIDATION_ERROR
      // ではなく NOT_FOUND になる。「limitが不正」ではなく「投稿が無い」が優先されたことの確認
      assertThatThrownBy(() -> commentService.getComments(ME_ID, POST_ID, 999, null))
          .isInstanceOf(NotFoundException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("#99 21件返るとhasNext=trueで20件だけ返す")
    void 次ページありの境界() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.of(Post.forCreate(ME_ID, "投稿本文")));
      List<CommentRow> rows =
          List.of(commentRow(1, ME_ID), commentRow(2, ME_ID), commentRow(3, ME_ID));
      when(commentMapper.findByPostId(eq(POST_ID), eq(null), eq(null), eq(3))).thenReturn(rows);

      var page = commentService.getComments(ME_ID, POST_ID, 2, null);

      assertThat(page.hasNext()).isTrue();
      assertThat(page.items()).hasSize(2);
    }
  }
}
