package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.ForbiddenException;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.file.FileService;
import com.example.snstimeline.post.dto.CreatePostRequest;
import com.example.snstimeline.post.dto.PostSummary;
import com.example.snstimeline.post.dto.UpdatePostRequest;
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
 * {@link PostService} の単体テスト（docs/11_test_design.md 8章、ケース #66〜#83）。
 *
 * <p><b>本章の中心は認可の順序。</b> 06_non_functional.md 5.3 の⑦「他人の投稿を削除すると403、
 * 存在しない投稿は404」は17項目で唯一「順序が正しいか」と明記された項目であり、 D-14 の規約
 * （①存在チェック→404、②所有者チェック→403、③更新できたかの競合チェック→404）を確かめる。
 *
 * <p>{@link LikeServiceTest} には無かった③の競合分岐（判定後に他の誰かが削除した経路）が加わる。
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

  private static final long ME_ID = 5L;
  private static final long OTHER_USER_ID = 999L;
  private static final long POST_ID = 100L;

  @Mock private PostMapper postMapper;
  @Mock private LikeMapper likeMapper;
  @Mock private PostImageMapper postImageMapper;
  @Mock private FileService fileService;

  @InjectMocks private PostService postService;

  /** 自分が投稿者である {@link Post} を返すよう設定する。存在確認・所有者判定にしか使われない。 */
  private void givenOwnPostExists() {
    when(postMapper.findById(POST_ID)).thenReturn(Optional.of(Post.forCreate(ME_ID, "本文")));
  }

  /** 他人が投稿者である {@link Post} を返すよう設定する。 */
  private void givenOtherUsersPostExists() {
    when(postMapper.findById(POST_ID))
        .thenReturn(Optional.of(Post.forCreate(OTHER_USER_ID, "他人の投稿")));
  }

  /**
   * {@link PostRow} は11項目でファクトリが無いため、ここで最小限のヘルパーを用意する。
   *
   * <p>{@code PostSummary.from} は実際に動く（モックされない）ため、null を渡すとNPEになりうる フィールド（author系）は実在しそうな値で埋める。
   */
  private static PostRow postRow(long id, long authorId) {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-01T12:00:00Z");
    return new PostRow(id, authorId, "本文", 0, 0, now, null, authorId, "user1", "表示名1", null);
  }

  @Nested
  @DisplayName("投稿の削除")
  class Delete {

    @Test
    @DisplayName("#66 投稿が無ければ404。softDeleteを呼ばない")
    void 投稿がなければ404() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> postService.delete(ME_ID, POST_ID))
          .isInstanceOf(NotFoundException.class);

      verify(postMapper, never()).softDelete(POST_ID);
    }

    /**
     * #67 <b>06_non_functional.md 5.3 ⑦そのもの。</b>
     *
     * <p>①存在チェックを通過した後、②所有者チェックで弾く。ここが逆順だと 「403が返った＝その投稿は実在する」と分かってしまい、非公開の情報が漏れる（D-14）。
     */
    @Test
    @DisplayName("#67 他人の投稿なら403。softDeleteを呼ばない")
    void 他人の投稿は削除できない() {
      givenOtherUsersPostExists();

      assertThatThrownBy(() -> postService.delete(ME_ID, POST_ID))
          .isInstanceOf(ForbiddenException.class);

      verify(postMapper, never()).softDelete(POST_ID);
    }

    @Test
    @DisplayName("#68 自分の投稿ならsoftDeleteが1回呼ばれ正常終了する")
    void 自分の投稿は削除できる() {
      givenOwnPostExists();
      when(postMapper.softDelete(POST_ID)).thenReturn(1);

      postService.delete(ME_ID, POST_ID);

      verify(postMapper).softDelete(POST_ID);
    }

    /** #69 ①②を通った後、実際に削除するまでの間に他の誰かが削除した場合の競合分岐（D-14 ③）。 */
    @Test
    @DisplayName("#69 削除件数0（競合）なら404")
    void 削除件数0なら404() {
      givenOwnPostExists();
      when(postMapper.softDelete(POST_ID)).thenReturn(0);

      assertThatThrownBy(() -> postService.delete(ME_ID, POST_ID))
          .isInstanceOf(NotFoundException.class);
    }

    /**
     * #70 <b>06_non_functional.md 5.3 ④「投稿を論理削除しても comment_count は変わらない」（非対称ルール）。</b>
     *
     * <p>投稿を消してもコメント自体は論理削除されないため、カウンタを触ってはいけない。 コメント削除（CommentServiceTest #86）とは非対称な扱いになる。
     */
    @Test
    @DisplayName("#70 投稿削除では postMapper のカウンタ更新に一切触れない（非対称ルール）")
    void 投稿削除ではコメント数を変えない() {
      givenOwnPostExists();
      when(postMapper.softDelete(POST_ID)).thenReturn(1);

      postService.delete(ME_ID, POST_ID);

      verify(postMapper, never()).decrementCommentCount(POST_ID);
      verify(postMapper, never()).incrementCommentCount(POST_ID);
    }
  }

  @Nested
  @DisplayName("投稿の更新")
  class Update {

    private final UpdatePostRequest request = new UpdatePostRequest("修正後の本文");

    @Test
    @DisplayName("#71 投稿が無ければ404。updateBodyを呼ばない")
    void 投稿がなければ404() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> postService.update(ME_ID, POST_ID, request))
          .isInstanceOf(NotFoundException.class);

      verify(postMapper, never()).updateBody(eq(POST_ID), anyString());
    }

    @Test
    @DisplayName("#72 他人の投稿なら403。updateBodyを呼ばない")
    void 他人の投稿は更新できない() {
      givenOtherUsersPostExists();

      assertThatThrownBy(() -> postService.update(ME_ID, POST_ID, request))
          .isInstanceOf(ForbiddenException.class);

      verify(postMapper, never()).updateBody(eq(POST_ID), anyString());
    }

    @Test
    @DisplayName("#73 自分の投稿なら更新され、更新後の値を返す")
    void 自分の投稿は更新できる() {
      givenOwnPostExists();
      when(postMapper.updateBody(POST_ID, "修正後の本文")).thenReturn(1);
      when(postMapper.findRowById(POST_ID)).thenReturn(Optional.of(postRow(POST_ID, ME_ID)));

      PostSummary summary = postService.update(ME_ID, POST_ID, request);

      verify(postMapper).updateBody(POST_ID, "修正後の本文");
      assertThat(summary.id()).isEqualTo(POST_ID);
    }

    @Test
    @DisplayName("#74 更新件数0（競合）なら404")
    void 更新件数0なら404() {
      givenOwnPostExists();
      when(postMapper.updateBody(POST_ID, "修正後の本文")).thenReturn(0);

      assertThatThrownBy(() -> postService.update(ME_ID, POST_ID, request))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  @DisplayName("投稿の作成")
  class Create {

    @Test
    @DisplayName("#75 画像なしなら assertOwnedBy を呼ばない")
    void 画像なしなら所有者チェックしない() {
      CreatePostRequest request = new CreatePostRequest("本文", null);
      when(postMapper.insert(ME_ID, "本文")).thenReturn(POST_ID);
      when(postMapper.findRowById(POST_ID)).thenReturn(Optional.of(postRow(POST_ID, ME_ID)));

      postService.create(ME_ID, request);

      verifyNoInteractions(fileService);
      verify(postMapper).insert(ME_ID, "本文");
    }

    /**
     * #76 <b>06_non_functional.md 5.3 ⑨「他人のfileIdを指定した投稿が403になる」（D-44）。</b>
     *
     * <p>所有者チェックが insert より前にあるため、弾かれた場合は投稿自体が保存されない。 チェックが後回しだと、投稿が保存されてから弾かれることになる。
     */
    @Test
    @DisplayName("#76 他人のfileIdなら assertOwnedBy が例外を投げ、postMapper に一切触れない")
    void 他人のファイルなら投稿は作られない() {
      CreatePostRequest request = new CreatePostRequest("本文", List.of(10L));
      doThrow(new ForbiddenException()).when(fileService).assertOwnedBy(ME_ID, 10L);

      assertThatThrownBy(() -> postService.create(ME_ID, request))
          .isInstanceOf(ForbiddenException.class);

      verifyNoInteractions(postMapper);
      verifyNoInteractions(postImageMapper);
    }

    @Test
    @DisplayName("#77 画像ありなら assertOwnedBy → insert の順（所有者チェックが先）")
    void 所有者チェックしてから保存する() {
      CreatePostRequest request = new CreatePostRequest("本文", List.of(10L));
      when(postMapper.insert(ME_ID, "本文")).thenReturn(POST_ID);
      when(postMapper.findRowById(POST_ID)).thenReturn(Optional.of(postRow(POST_ID, ME_ID)));

      postService.create(ME_ID, request);

      InOrder inOrder = inOrder(fileService, postMapper);
      inOrder.verify(fileService).assertOwnedBy(ME_ID, 10L);
      inOrder.verify(postMapper).insert(ME_ID, "本文");
    }

    @Test
    @DisplayName("#78 displayOrder が 0, 1 の順で渡る")
    void 画像の表示順が渡る() {
      CreatePostRequest request = new CreatePostRequest("本文", List.of(10L, 20L));
      when(postMapper.insert(ME_ID, "本文")).thenReturn(POST_ID);
      when(postMapper.findRowById(POST_ID)).thenReturn(Optional.of(postRow(POST_ID, ME_ID)));

      postService.create(ME_ID, request);

      verify(postImageMapper).insert(POST_ID, 10L, 0);
      verify(postImageMapper).insert(POST_ID, 20L, 1);
    }
  }

  @Nested
  @DisplayName("一覧取得とページング")
  class Timeline {

    @Test
    @DisplayName("#79 limitが未指定なら既定値20が使われる")
    void limit未指定は既定値() {
      when(postMapper.findTimeline(eq(TimelineTab.ALL), eq(ME_ID), eq(null), eq(null), eq(21)))
          .thenReturn(List.of());

      postService.getTimeline(ME_ID, TimelineTab.ALL, null, null);

      verify(postMapper).findTimeline(TimelineTab.ALL, ME_ID, null, null, 21);
    }

    @Test
    @DisplayName("#80 limitが0または51なら400。Mapperに触れない")
    void limitが範囲外なら400() {
      assertThatThrownBy(() -> postService.getTimeline(ME_ID, TimelineTab.ALL, 0, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
      assertThatThrownBy(() -> postService.getTimeline(ME_ID, TimelineTab.ALL, 51, null))
          .isInstanceOf(ApiException.class);

      verifyNoInteractions(postMapper);
    }

    /**
     * #81 <b>D-06「limit+1件取得し、1件多く返ってきたら次ページがあると判定する」。</b>
     *
     * <p>21件返れば「次がある」と判断し、20件だけ返す。境界を間違えると最終ページが 表示されない、または同じ投稿が2回出る。
     */
    @Test
    @DisplayName("#81 21件返るとhasNext=trueで20件だけ返す")
    void 次ページありの境界() {
      List<PostRow> rows = List.of(postRow(1, ME_ID), postRow(2, ME_ID), postRow(3, ME_ID));
      when(postMapper.findTimeline(eq(TimelineTab.ALL), eq(ME_ID), eq(null), eq(null), eq(3)))
          .thenReturn(rows);
      when(likeMapper.findLikedPostIds(eq(ME_ID), anyList())).thenReturn(List.of());
      when(postImageMapper.findByPostIds(anyList())).thenReturn(List.of());

      var page = postService.getTimeline(ME_ID, TimelineTab.ALL, 2, null);

      assertThat(page.hasNext()).isTrue();
      assertThat(page.items()).hasSize(2);
    }

    @Test
    @DisplayName("#82 limitちょうどしか返らなければhasNext=false")
    void 次ページなしの境界() {
      List<PostRow> rows = List.of(postRow(1, ME_ID), postRow(2, ME_ID));
      when(postMapper.findTimeline(eq(TimelineTab.ALL), eq(ME_ID), eq(null), eq(null), eq(3)))
          .thenReturn(rows);
      when(likeMapper.findLikedPostIds(eq(ME_ID), anyList())).thenReturn(List.of());
      when(postImageMapper.findByPostIds(anyList())).thenReturn(List.of());

      var page = postService.getTimeline(ME_ID, TimelineTab.ALL, 2, null);

      assertThat(page.hasNext()).isFalse();
      assertThat(page.items()).hasSize(2);
    }

    /** #83 N+1回避（04_data_model.md 6.6）。0件のときに一括取得クエリまで飛ばすのは無駄である。 */
    @Test
    @DisplayName("#83 投稿が0件ならlikeMapperとpostImageMapperを呼ばない")
    void 空リストならMapperを呼ばない() {
      when(postMapper.findTimeline(eq(TimelineTab.ALL), eq(ME_ID), eq(null), eq(null), anyInt()))
          .thenReturn(List.of());

      postService.getTimeline(ME_ID, TimelineTab.ALL, null, null);

      verifyNoInteractions(likeMapper);
      verifyNoInteractions(postImageMapper);
    }
  }
}
