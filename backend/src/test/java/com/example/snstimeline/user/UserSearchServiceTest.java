package com.example.snstimeline.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.OffsetPage;
import com.example.snstimeline.follow.FollowMapper;
import com.example.snstimeline.user.dto.UserListItem;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserSearchService} の単体テスト（docs/11_test_design.md 13章、ケース #139〜#160）。
 *
 * <p>本クラスの中心は {@code escapeLikePattern}。{@code %} のエスケープを忘れると {@code q=%}
 * で全ユーザーが列挙される（パラメータバインディングでは防げない情報漏洩、04_data_model.md 6.5）。
 */
@ExtendWith(MockitoExtension.class)
class UserSearchServiceTest {

  private static final long ME_ID = 5L;

  @Mock private UserMapper userMapper;
  @Mock private FollowMapper followMapper;

  @InjectMocks private UserSearchService userSearchService;

  private ArgumentCaptor<String> qEscapedCaptor;

  private void givenTotal(long total) {
    when(userMapper.countSearchUsers(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            eq(ME_ID)))
        .thenReturn(total);
  }

  @Nested
  @DisplayName("LIKEエスケープ")
  class LikeEscape {

    /** #139 <b>本節で最重要。</b> 17項目相当の「ファイル所有者チェック」と並ぶ情報漏洩の防止策。 */
    @Test
    @DisplayName("#139 q=%はエスケープされ、countSearchUsersに渡るqEscapedは\\%になる")
    void パーセントはエスケープされる() {
      givenTotal(0);

      userSearchService.search(ME_ID, "%", null, null);

      qEscapedCaptor = ArgumentCaptor.forClass(String.class);
      verify(userMapper)
          .countSearchUsers(
              qEscapedCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), eq(ME_ID));
      assertThat(qEscapedCaptor.getValue()).isEqualTo("\\%");
    }

    @Test
    @DisplayName("#140 q=a_bはエスケープされ、a\\_bになる")
    void アンダースコアはエスケープされる() {
      givenTotal(0);

      userSearchService.search(ME_ID, "a_b", null, null);

      qEscapedCaptor = ArgumentCaptor.forClass(String.class);
      verify(userMapper)
          .countSearchUsers(
              qEscapedCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), eq(ME_ID));
      assertThat(qEscapedCaptor.getValue()).isEqualTo("a\\_b");
    }

    @Test
    @DisplayName("#141 q=\\（バックスラッシュ1文字）は\\\\になる")
    void バックスラッシュはエスケープされる() {
      givenTotal(0);

      userSearchService.search(ME_ID, "\\", null, null);

      qEscapedCaptor = ArgumentCaptor.forClass(String.class);
      verify(userMapper)
          .countSearchUsers(
              qEscapedCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), eq(ME_ID));
      assertThat(qEscapedCaptor.getValue()).isEqualTo("\\\\");
    }

    /**
     * #142 <b>置換順序の回帰テスト。</b> バックスラッシュを先に置換するため、{@code \%} の {@code \} は {@code \\} に、 その後の {@code
     * %} は {@code \%} になる。順序を逆にすると、{@code %} を {@code \%} に変換した後の {@code \} まで 二重化されてしまい、結果が変わる。
     */
    @Test
    @DisplayName("#142 q=\\%（バックスラッシュ＋パーセント）は\\\\\\%になる（置換順序が重要）")
    void 複合パターンの置換順序() {
      givenTotal(0);

      userSearchService.search(ME_ID, "\\%", null, null);

      qEscapedCaptor = ArgumentCaptor.forClass(String.class);
      verify(userMapper)
          .countSearchUsers(
              qEscapedCaptor.capture(), org.mockito.ArgumentMatchers.anyString(), eq(ME_ID));
      assertThat(qEscapedCaptor.getValue()).isEqualTo("\\\\\\%");
    }

    @Test
    @DisplayName("#143 countSearchUsersにはqEscapedとkeyword（生の値）の両方が渡る")
    void エスケープ済みと生の値の両方が渡る() {
      givenTotal(0);

      userSearchService.search(ME_ID, "たろう", null, null);

      verify(userMapper).countSearchUsers("たろう", "たろう", ME_ID);
    }
  }

  @Nested
  @DisplayName("バリデーション")
  class Validation {

    @Test
    @DisplayName("#144 q=nullなら400")
    void qがnull() {
      assertThatThrownBy(() -> userSearchService.search(ME_ID, null, null, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#145 空白のみなら400（トリム後0文字）")
    void 空白のみ() {
      assertThatThrownBy(() -> userSearchService.search(ME_ID, "   ", null, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#146 コードポイント1文字なら通る")
    void 最小長は通る() {
      givenTotal(0);

      userSearchService.search(ME_ID, "a", null, null);

      verify(userMapper).countSearchUsers("a", "a", ME_ID);
    }

    /** #147 絵文字はサロゲートペアでlength()は2文字扱いだが、コードポイント数は1。50個なら通る。 */
    @Test
    @DisplayName("#147 絵文字50個（コードポイント50）は通る")
    void 絵文字50個は通る() {
      givenTotal(0);
      String emoji50 = "😀".repeat(50);

      userSearchService.search(ME_ID, emoji50, null, null);

      verify(userMapper).countSearchUsers(emoji50, emoji50, ME_ID);
    }

    @Test
    @DisplayName("#148 コードポイント51文字なら400")
    void 最大長超過() {
      String tooLong = "あ".repeat(51);

      assertThatThrownBy(() -> userSearchService.search(ME_ID, tooLong, null, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#149 page=nullなら0として扱う")
    void ページ省略は0() {
      givenTotal(0);

      OffsetPage<UserListItem> page = userSearchService.search(ME_ID, "a", null, null);

      assertThat(page.page()).isZero();
    }

    @Test
    @DisplayName("#150 page=-1なら400")
    void ページが負数() {
      assertThatThrownBy(() -> userSearchService.search(ME_ID, "a", -1, null))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#151 pageに上限は無い（page=100000でも400にならない）")
    void ページに上限は無い() {
      givenTotal(0);

      OffsetPage<UserListItem> page = userSearchService.search(ME_ID, "a", 100000, null);

      assertThat(page.page()).isEqualTo(100000);
      assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("#152 size=nullなら既定値20")
    void サイズ省略は既定値() {
      givenTotal(0);

      OffsetPage<UserListItem> page = userSearchService.search(ME_ID, "a", null, null);

      assertThat(page.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("#153 size=0なら400")
    void サイズが0() {
      assertThatThrownBy(() -> userSearchService.search(ME_ID, "a", null, 0))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("#154 size=50なら通る")
    void サイズ上限は通る() {
      givenTotal(0);

      OffsetPage<UserListItem> page = userSearchService.search(ME_ID, "a", null, 50);

      assertThat(page.size()).isEqualTo(50);
    }

    @Test
    @DisplayName("#155 size=51なら400")
    void サイズ上限超過() {
      assertThatThrownBy(() -> userSearchService.search(ME_ID, "a", null, 51))
          .isInstanceOf(ApiException.class)
          .extracting(e -> ((ApiException) e).getErrorCode())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
  }

  @Nested
  @DisplayName("ページング・N+1回避")
  class Paging {

    /** #156 総件数0が確定した時点で早期リターンする。無駄な検索クエリを飛ばさない。 */
    @Test
    @DisplayName("#156 総件数0ならsearchUsersを呼ばない")
    void 総件数0なら検索しない() {
      givenTotal(0);

      OffsetPage<UserListItem> page = userSearchService.search(ME_ID, "存在しない", null, null);

      verify(userMapper, never())
          .searchUsers(
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.anyLong(),
              org.mockito.ArgumentMatchers.anyInt(),
              org.mockito.ArgumentMatchers.anyInt());
      assertThat(page.items()).isEmpty();
      assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("#157 offsetはpage*sizeで計算される")
    void オフセットの計算() {
      givenTotal(100);
      when(userMapper.searchUsers("a", "a", ME_ID, 20, 40)).thenReturn(List.of());

      userSearchService.search(ME_ID, "a", 2, 20);

      verify(userMapper).searchUsers("a", "a", ME_ID, 20, 40);
    }

    @Test
    @DisplayName("#158 検索結果のisMeは常にfalse")
    void 検索結果は自分自身を含まない() {
      givenTotal(1);
      when(userMapper.searchUsers("a", "a", ME_ID, 20, 0))
          .thenReturn(List.of(new UserSearchRow(OTHER_ID(), "user1", "表示名1", null, null)));
      when(followMapper.findFollowedUserIds(eq(ME_ID), anyList())).thenReturn(List.of());

      OffsetPage<UserListItem> page = userSearchService.search(ME_ID, "a", null, null);

      assertThat(page.items()).hasSize(1);
      assertThat(page.items().get(0).isMe()).isFalse();
    }

    /** #160 ページが空ならフォロー判定の一括取得を呼ばない（N+1回避）。 */
    @Test
    @DisplayName("#160 ページが空ならfindFollowedUserIdsを呼ばない")
    void 空ページならフォロー判定を呼ばない() {
      givenTotal(1);
      when(userMapper.searchUsers("a", "a", ME_ID, 20, 0)).thenReturn(List.of());

      userSearchService.search(ME_ID, "a", null, null);

      verify(followMapper, never())
          .findFollowedUserIds(org.mockito.ArgumentMatchers.anyLong(), anyList());
    }

    private long OTHER_ID() {
      return 42L;
    }
  }
}
