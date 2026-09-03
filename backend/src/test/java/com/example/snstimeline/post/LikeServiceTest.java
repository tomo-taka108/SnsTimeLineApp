package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.post.dto.LikeResponse;
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
 * {@link LikeService} の単体テスト（docs/11_test_design.md 6章、ケース #54〜#65）。
 *
 * <p><b>部品のテスト（1〜3章）と性質が違う。</b> あちらは「入力 → 出力」を見るだけだったが、 ここでは<b>何が呼ばれたか／呼ばれなかったか</b>を検証する。Mapper を
 * Mockito の偽物に 差し替えるため、DBもDockerも要らず1秒未満で終わる。
 *
 * <p>中心にあるのは<b>冪等性</b>——同じ操作を何回繰り返しても結果が変わらない性質。 いいねを2回押してもカウンタが2にならないことを担保する（06_non_functional.md
 * 5.3 の①②、D-01 / D-34）。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

  private static final long ME_ID = 5L;
  private static final long POST_ID = 100L;
  private static final long AUTHOR_ID = 42L;

  @Mock private PostMapper postMapper;
  @Mock private LikeMapper likeMapper;

  @InjectMocks private LikeService likeService;

  /** 投稿が存在する状態にする。{@code LikeService} は中身を読まないため、最小限の値でよい。 */
  private void givenPostExists() {
    when(postMapper.findById(POST_ID)).thenReturn(Optional.of(Post.forCreate(AUTHOR_ID, "テスト投稿")));
  }

  @Nested
  @DisplayName("いいねする")
  class Like {

    @Test
    @DisplayName("#54 未いいねなら insert と incrementLikeCount が各1回呼ばれる")
    void 未いいねなら登録する() {
      givenPostExists();
      when(likeMapper.exists(POST_ID, ME_ID)).thenReturn(false);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(1);

      LikeResponse response = likeService.like(ME_ID, POST_ID);

      verify(likeMapper).insert(POST_ID, ME_ID);
      verify(likeMapper).incrementLikeCount(POST_ID);
      assertThat(response.likeCount()).isEqualTo(1);
      assertThat(response.isLikedByMe()).isTrue();
    }

    /**
     * #55 <b>17項目①「同じ投稿に2回いいねしてもカウンタが2にならない」そのもの。</b>
     *
     * <p>事前SELECT（{@code exists}）で弾く方式（D-34）。「やっていないこと」を確かめるため {@code never()}
     * を使う。ガードが外れると、連打や通信の再送でカウンタが際限なく増える。
     */
    @Test
    @DisplayName("#55 いいね済みなら insert も incrementLikeCount も呼ばない（冪等）")
    void いいね済みなら何もしない() {
      givenPostExists();
      when(likeMapper.exists(POST_ID, ME_ID)).thenReturn(true);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(1);

      LikeResponse response = likeService.like(ME_ID, POST_ID);

      verify(likeMapper, never()).insert(POST_ID, ME_ID);
      verify(likeMapper, never()).incrementLikeCount(POST_ID);
      // 2回目でもレスポンスは1回目と同じ。カウンタは増えない
      assertThat(response.likeCount()).isEqualTo(1);
      assertThat(response.isLikedByMe()).isTrue();
    }

    /** #60 カウンタを先に増やすと、実体が無いのに数だけ増えた状態が一瞬生じる。 同一トランザクション内ではあるが、順序自体を仕様として固定しておく（D-01）。 */
    @Test
    @DisplayName("#60 insert → incrementLikeCount の順で呼ばれる")
    void 登録してからカウンタを増やす() {
      givenPostExists();
      when(likeMapper.exists(POST_ID, ME_ID)).thenReturn(false);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(1);

      likeService.like(ME_ID, POST_ID);

      InOrder inOrder = inOrder(likeMapper);
      inOrder.verify(likeMapper).insert(POST_ID, ME_ID);
      inOrder.verify(likeMapper).incrementLikeCount(POST_ID);
    }

    @Test
    @DisplayName("#61 いいね数は findLikeCount の値をそのまま返す（自分で加算しない）")
    void カウントはmapperの値をそのまま返す() {
      givenPostExists();
      when(likeMapper.exists(POST_ID, ME_ID)).thenReturn(false);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(7);

      LikeResponse response = likeService.like(ME_ID, POST_ID);

      // 7 のまま。ここで +1 していると画面の数字が二重加算でずれる
      assertThat(response.likeCount()).isEqualTo(7);
    }

    /**
     * #64 <b>#55 と対になるテスト。</b>
     *
     * <p>{@code findLikeCount} は冪等性ガードの<b>外側</b>にあるため、2回目のいいねでも 呼ばれなければならない。将来 {@code return}
     * ごとガードの内側へ移す変更が入ると、 2回目のレスポンスが 0 になる。それを検出する。
     */
    @Test
    @DisplayName("#64 いいね済みでも findLikeCount は呼ばれる（ガードの外側にある）")
    void いいね済みでもカウントは取得する() {
      givenPostExists();
      when(likeMapper.exists(POST_ID, ME_ID)).thenReturn(true);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(3);

      LikeResponse response = likeService.like(ME_ID, POST_ID);

      verify(likeMapper).findLikeCount(POST_ID);
      assertThat(response.likeCount()).isEqualTo(3);
    }

    /**
     * #65 <b>引数の取り違え検出。</b>
     *
     * <p>{@code like(meId, postId)} に対し {@code insert(postId, userId)} と引数の順序が逆で、 どちらも {@code Long}
     * のため<b>取り違えてもコンパイルが通る</b>。間違えるとユーザーIDと 投稿IDが入れ替わるが、いいね数は増えるので<b>画面上は正常に見える</b>。
     */
    @Test
    @DisplayName("#65 mapper には (postId, meId) の順で渡す（引数が逆転していない）")
    void 引数の順序が正しい() {
      givenPostExists();
      when(likeMapper.exists(POST_ID, ME_ID)).thenReturn(false);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(1);

      likeService.like(ME_ID, POST_ID);

      // POST_ID=100, ME_ID=5。逆に渡していれば「ユーザー100が投稿5にいいね」になる
      verify(likeMapper).exists(POST_ID, ME_ID);
      verify(likeMapper).insert(POST_ID, ME_ID);
    }
  }

  @Nested
  @DisplayName("いいねを外す")
  class Unlike {

    @Test
    @DisplayName("#56 いいね済みなら delete が1件返り decrementLikeCount が呼ばれる")
    void いいね済みなら解除する() {
      givenPostExists();
      when(likeMapper.delete(POST_ID, ME_ID)).thenReturn(1);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(0);

      LikeResponse response = likeService.unlike(ME_ID, POST_ID);

      verify(likeMapper).decrementLikeCount(POST_ID);
      assertThat(response.isLikedByMe()).isFalse();
    }

    /**
     * #57 <b>17項目②「いいねしていない状態で解除しても壊れない」そのもの。</b>
     *
     * <p>削除件数が0なら減算しない。ガードが外れると、いいねしていない投稿を解除するたびに カウンタが減り、<b>負の値になる</b>（DBのCHECK制約に引っかかるか、
     * 制約が無ければ画面に「-1件」と出る）。
     */
    @Test
    @DisplayName("#57 未いいねなら delete が0件で decrementLikeCount を呼ばない（冪等）")
    void 未いいねなら減算しない() {
      givenPostExists();
      when(likeMapper.delete(POST_ID, ME_ID)).thenReturn(0);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(0);

      likeService.unlike(ME_ID, POST_ID);

      verify(likeMapper, never()).decrementLikeCount(POST_ID);
    }

    @Test
    @DisplayName("#62 isLikedByMe は false を返す")
    void 解除後はfalseを返す() {
      givenPostExists();
      when(likeMapper.delete(POST_ID, ME_ID)).thenReturn(1);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(2);

      LikeResponse response = likeService.unlike(ME_ID, POST_ID);

      // ここが true のままだとハートの色が戻らない
      assertThat(response.isLikedByMe()).isFalse();
      assertThat(response.likeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("#63 いいね数が0でも正常に返る（負数にしない）")
    void カウントが0でも返る() {
      givenPostExists();
      when(likeMapper.delete(POST_ID, ME_ID)).thenReturn(0);
      when(likeMapper.findLikeCount(POST_ID)).thenReturn(0);

      LikeResponse response = likeService.unlike(ME_ID, POST_ID);

      assertThat(response.likeCount()).isZero();
      assertThat(response.likeCount()).isNotNegative();
    }
  }

  /**
   * 存在チェックが最初に来ること（D-14）。
   *
   * <p>先に {@code likes} を触ってしまうと、どの投稿にも紐付かないゴミデータが残りうる。 <b>404を返すだけでなく、Mapper
   * を一度も呼んでいないこと</b>まで確かめる。
   */
  @Nested
  @DisplayName("投稿が存在しない")
  class PostNotFound {

    @Test
    @DisplayName("#58 いいね時に404を投げ、likeMapper に一切触れない")
    void いいねは404になる() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> likeService.like(ME_ID, POST_ID))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(likeMapper);
    }

    @Test
    @DisplayName("#59 いいね解除時に404を投げ、likeMapper に一切触れない")
    void いいね解除は404になる() {
      when(postMapper.findById(POST_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> likeService.unlike(ME_ID, POST_ID))
          .isInstanceOf(NotFoundException.class);

      verifyNoInteractions(likeMapper);
    }
  }
}
