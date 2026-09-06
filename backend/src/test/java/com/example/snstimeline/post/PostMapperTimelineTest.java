package com.example.snstimeline.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.snstimeline.common.CursorCodec;
import com.example.snstimeline.support.AbstractIntegrationTest;
import com.example.snstimeline.support.TestFixtures;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PostMapper} のタイムライン系SQLの結合テスト（docs/11_test_design.md 19章、ケース #204〜#217）。
 *
 * <p><b>本PRで最も重要なテストクラス。</b> 必須テスト項目（06_non_functional.md 5.3）の残り2項目
 * 「論理削除の除外」「カーソルページネーション」を、ここで実SQLに対して検証する。
 *
 * <p>Service層の単体テスト（6〜17章）はMapperをモックしていたため、SQLに {@code deleted_at IS NULL} が 付いているか、{@code ORDER
 * BY} にタイブレーカーがあるかは<b>原理的に検証できなかった</b>。 実DB（Testcontainers）を使う本章で初めて確認できる。
 */
@SpringBootTest
@Transactional
class PostMapperTimelineTest extends AbstractIntegrationTest {

  @Autowired private PostMapper postMapper;
  @Autowired private TestFixtures fixtures;

  /** 十分に大きな limit。件数で切られていないことを明確にする。 */
  private static final int NO_LIMIT = 100;

  @Nested
  @DisplayName("論理削除の除外")
  class SoftDelete {

    /**
     * #204 <b>必須項目「論理削除の除外」の中心。</b>
     *
     * <p>この1本が落ちると、削除したはずの投稿が全ユーザーのタイムラインに出続ける。 MyBatis には Hibernate の {@code @SQLRestriction}
     * に相当する自動除外が無く、 各SQLに手で書く必要があるため（D-02 / D-25）、書き忘れは現実的に起こる。
     */
    @Test
    @DisplayName("#204 論理削除した投稿はタイムラインに出ない")
    void 削除済み投稿はタイムラインに出ない() {
      long me = fixtures.user("alice");
      long alive = fixtures.post(me, "生きている投稿");
      long deleted = fixtures.post(me, "消した投稿");
      fixtures.softDeletePost(deleted);

      List<PostRow> rows = postMapper.findTimeline(TimelineTab.ALL, me, null, null, NO_LIMIT);

      assertThat(rows).extracting(PostRow::id).containsExactly(alive).doesNotContain(deleted);
    }

    /** #205 投稿自体は生きていても、投稿者が退会していればタイムラインに出さない（JOIN側の絞り込み）。 */
    @Test
    @DisplayName("#205 退会したユーザーの投稿はタイムラインに出ない")
    void 退会ユーザーの投稿は出ない() {
      long me = fixtures.user("alice");
      long gone = fixtures.user("bob");
      fixtures.post(gone, "退会者の投稿");
      fixtures.softDeleteUser(gone);

      List<PostRow> rows = postMapper.findTimeline(TimelineTab.ALL, me, null, null, NO_LIMIT);

      assertThat(rows).isEmpty();
    }

    /** #206 {@code findByUserId} は findTimeline とは別のSQL。同じ絞り込みが要る。 */
    @Test
    @DisplayName("#206 論理削除した投稿はユーザーの投稿一覧にも出ない")
    void 削除済み投稿はユーザー投稿一覧に出ない() {
      long me = fixtures.user("alice");
      long alive = fixtures.post(me, "生きている投稿");
      long deleted = fixtures.post(me, "消した投稿");
      fixtures.softDeletePost(deleted);

      List<PostRow> rows = postMapper.findByUserId(me, null, null, NO_LIMIT);

      assertThat(rows).extracting(PostRow::id).containsExactly(alive);
    }

    @Test
    @DisplayName("#207 退会したユーザーの投稿一覧は空になる")
    void 退会ユーザーの投稿一覧は空() {
      long gone = fixtures.user("bob");
      fixtures.post(gone, "退会者の投稿");
      fixtures.softDeleteUser(gone);

      List<PostRow> rows = postMapper.findByUserId(gone, null, null, NO_LIMIT);

      assertThat(rows).isEmpty();
    }

    /** #208 詳細取得。Controller層の404（#273）はこの空Optionalが根拠になる。 */
    @Test
    @DisplayName("#208 論理削除した投稿は findRowById で取得できない")
    void 削除済み投稿は詳細取得できない() {
      long me = fixtures.user("alice");
      long deleted = fixtures.post(me, "消した投稿");
      fixtures.softDeletePost(deleted);

      assertThat(postMapper.findRowById(deleted)).isEmpty();
    }

    /**
     * #209 認可用の軽量クエリ。ここが漏れると、削除済み投稿をもう一度削除・編集できてしまい、 {@code comment_count} の二重減算（CHECK制約違反）に繋がる。
     */
    @Test
    @DisplayName("#209 論理削除した投稿は findById でも取得できない")
    void 削除済み投稿は認可用クエリでも取れない() {
      long me = fixtures.user("alice");
      long deleted = fixtures.post(me, "消した投稿");
      fixtures.softDeletePost(deleted);

      assertThat(postMapper.findById(deleted)).isEmpty();
    }

    /** #210 {@code findRowById} は users とJOINするため、投稿者が退会していると取得できない。 */
    @Test
    @DisplayName("#210 投稿者が退会していると findRowById では取得できない")
    void 退会者の投稿は詳細取得できない() {
      long gone = fixtures.user("bob");
      long post = fixtures.post(gone, "退会者の投稿");
      fixtures.softDeleteUser(gone);

      assertThat(postMapper.findRowById(post)).isEmpty();
    }

    /**
     * #211 <b>#210 との非対称性を意図的に固定する。</b>
     *
     * <p>{@code findById} は posts のカラムしか見ないため、投稿者が退会していても取得できる。 これは仕様であって漏れではない —— {@code
     * PostService.delete} などの認可は 「存在→404、所有者→403」の順序（D-14）で判定する必要があり、 ここに users
     * のJOINを足すと退会者の投稿が404になって順序が崩れる。
     */
    @Test
    @DisplayName("#211 投稿者が退会していても findById では取得できる（#210との非対称は仕様）")
    void 退会者の投稿も認可用クエリでは取れる() {
      long gone = fixtures.user("bob");
      long post = fixtures.post(gone, "退会者の投稿");
      fixtures.softDeleteUser(gone);

      assertThat(postMapper.findById(post)).isPresent();
    }
  }

  /**
   * カーソルページネーションのタイブレーカー（必須項目・D-33 / 04_data_model.md 7章）。
   *
   * <p><b>同一 {@code created_at} の投稿が2件以上あっても、取りこぼしも重複も起きないこと</b>を確認する。 {@code ORDER BY p.created_at
   * DESC, p.id DESC} の {@code , p.id DESC} と、 行値比較 {@code (p.created_at, p.id) < (?, ?)}
   * の両方が効いて初めて成立する。
   *
   * <p>カーソルは必ず実物の {@link CursorCodec} を往復させる。往復させないとSQLだけのテストになり、 コーデックがマイクロ秒を落とす回帰を検出できなくなる。
   */
  @Nested
  @DisplayName("カーソルのタイブレーカー")
  class CursorTiebreaker {

    /** {@code created_at} は必ずマイクロ秒に丸める（PostgreSQLとCursorCodecがマイクロ秒精度のため）。 */
    private OffsetDateTime instant() {
      return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /** 実物のコーデックを往復させてカーソルを作る。 */
    private CursorCodec.Cursor cursorOf(PostRow last) {
      return CursorCodec.decode(CursorCodec.encode(last.createdAt(), last.id()));
    }

    /** #212 同一時刻2件を1件ずつページングし、両方がちょうど1回ずつ現れることを確認する。 */
    @Test
    @DisplayName("#212 同一created_atの2件が重複も欠落もなくページングできる")
    void 同時刻2件がページングできる() {
      long me = fixtures.user("alice");
      OffsetDateTime t = instant();
      long p1 = fixtures.postAt(me, "同時刻A", t);
      long p2 = fixtures.postAt(me, "同時刻B", t);
      assertThat(p2).isGreaterThan(p1); // BIGSERIALの単調増加を前提にしていることを明示

      // limit は「欲しい件数 + 1」。21件目の有無で次ページを判定する（D-06）
      List<PostRow> page1 = postMapper.findTimeline(TimelineTab.ALL, me, null, null, 2);
      assertThat(page1).extracting(PostRow::id).containsExactly(p2, p1);

      CursorCodec.Cursor c = cursorOf(page1.get(0)); // 1件目までを返した体で次を取る
      List<PostRow> page2 = postMapper.findTimeline(TimelineTab.ALL, me, c.createdAt(), c.id(), 2);

      assertThat(page2).extracting(PostRow::id).containsExactly(p1);
    }

    /**
     * #213 <b>#212 だけでは不十分。</b>
     *
     * <p>同一 {@code created_at} が3件あるところをページサイズ1で最後まで辿り、 <b>全件がちょうど1回ずつ得られること</b>（重複も欠落も無いこと）を確認する。
     *
     * <p>ここで効いているのは主に行値比較 {@code (created_at, id) < (?, ?)} の方で、 {@code ORDER BY} の {@code , p.id
     * DESC} は「同時刻内の並びを決定的にする」役割。 両方が揃って初めて、ページを跨いだ取りこぼしが起きないことが保証される。
     */
    @Test
    @DisplayName("#213 同一created_at3件をページサイズ1で辿っても全件ちょうど1回ずつ取れる")
    void 同時刻3件を辿れる() {
      long me = fixtures.user("alice");
      OffsetDateTime t = instant();
      long p1 = fixtures.postAt(me, "同時刻A", t);
      long p2 = fixtures.postAt(me, "同時刻B", t);
      long p3 = fixtures.postAt(me, "同時刻C", t);

      List<Long> collected = new java.util.ArrayList<>();
      CursorCodec.Cursor cursor = null;
      for (int i = 0; i < 10; i++) { // 無限ループ防止の上限
        // ページサイズ1（+1件で次ページ判定）
        List<PostRow> rows =
            cursor == null
                ? postMapper.findTimeline(TimelineTab.ALL, me, null, null, 2)
                : postMapper.findTimeline(TimelineTab.ALL, me, cursor.createdAt(), cursor.id(), 2);
        if (rows.isEmpty()) {
          break;
        }
        boolean hasNext = rows.size() > 1;
        PostRow last = rows.get(0);
        collected.add(last.id());
        if (!hasNext) {
          break;
        }
        cursor = cursorOf(last);
      }

      // 順序ではなく「全件がちょうど1回ずつ」を検証する。
      // 同時刻内の並び自体はタイブレーカーが決めるが、ここで守りたいのは取りこぼしの無さ
      assertThat(collected).containsExactlyInAnyOrder(p1, p2, p3).doesNotHaveDuplicates();
    }

    /**
     * #213b 同一 {@code created_at} が多数あっても、ページングで全件をちょうど1回ずつ取得できる。
     *
     * <p>#213 の件数を増やした版。同時刻グループがページサイズより大きい状況を作り、 グループの内側で何度もページ境界をまたがせる。
     *
     * <p><b>ミューテーションで分かったこと（21章に記録）:</b> {@code ORDER BY} から {@code , p.id DESC} を外しても このテストも #213
     * も緑のままだった。ページングの正しさを実際に守っているのは <b>行値比較 {@code (created_at, id) < (?, ?)} の方</b>で、{@code ORDER
     * BY} のタイブレーカーは 「同時刻内の並びを決定的にする」役割だと確認できた。並び順そのものは PostgreSQL
     * の実行計画に左右されるため、テストで安定して固定できない（実行計画次第で 昇順にも降順にもなる）。<b>取りこぼしの無さ</b>を検証対象にするのが正しい。
     */
    @Test
    @DisplayName("#213b 同一created_atが8件でもページングで全件ちょうど1回ずつ取れる")
    void 同時刻多数でも取りこぼさない() {
      long me = fixtures.user("alice");
      OffsetDateTime t = instant();
      List<Long> expected = new java.util.ArrayList<>();
      for (int i = 0; i < 8; i++) {
        expected.add(fixtures.postAt(me, "同時刻" + i, t));
      }

      List<Long> collected = new java.util.ArrayList<>();
      CursorCodec.Cursor cursor = null;
      for (int i = 0; i < 20; i++) { // 無限ループ防止の上限
        // ページサイズ3（+1件で次ページ判定）
        List<PostRow> rows =
            cursor == null
                ? postMapper.findTimeline(TimelineTab.ALL, me, null, null, 4)
                : postMapper.findTimeline(TimelineTab.ALL, me, cursor.createdAt(), cursor.id(), 4);
        if (rows.isEmpty()) {
          break;
        }
        boolean hasNext = rows.size() > 3;
        List<PostRow> page = hasNext ? rows.subList(0, 3) : rows;
        page.forEach(r -> collected.add(r.id()));
        if (!hasNext) {
          break;
        }
        cursor = cursorOf(page.get(page.size() - 1));
      }

      assertThat(collected).containsExactlyInAnyOrderElementsOf(expected).doesNotHaveDuplicates();
    }

    /** #214 時刻が混在していても、created_at降順・同時刻内はid降順で並ぶこと。 */
    @Test
    @DisplayName("#214 時刻が混在しても created_at DESC, id DESC の順で並ぶ")
    void 時刻混在でも順序が正しい() {
      long me = fixtures.user("alice");
      OffsetDateTime t = instant();
      long older = fixtures.postAt(me, "古い", t.minusSeconds(1));
      long same1 = fixtures.postAt(me, "同時刻A", t);
      long same2 = fixtures.postAt(me, "同時刻B", t);
      long newer = fixtures.postAt(me, "新しい", t.plusSeconds(1));

      List<PostRow> rows = postMapper.findTimeline(TimelineTab.ALL, me, null, null, NO_LIMIT);

      assertThat(rows).extracting(PostRow::id).containsExactly(newer, same2, same1, older);
    }

    /**
     * #215 カーソルの境界は<b>排他</b>（{@code <} であり {@code <=} ではない）。
     *
     * <p>{@code <=} に変わると、カーソルに指定した投稿自身が毎ページ再登場して無限に重複する。
     */
    @Test
    @DisplayName("#215 カーソルに指定した投稿自身は次ページに含まれない")
    void カーソル境界は排他() {
      long me = fixtures.user("alice");
      OffsetDateTime t = instant();
      long p1 = fixtures.postAt(me, "古い", t.minusSeconds(1));
      long p2 = fixtures.postAt(me, "新しい", t);

      CursorCodec.Cursor c = CursorCodec.decode(CursorCodec.encode(t, p2));
      List<PostRow> rows =
          postMapper.findTimeline(TimelineTab.ALL, me, c.createdAt(), c.id(), NO_LIMIT);

      assertThat(rows).extracting(PostRow::id).containsExactly(p1).doesNotContain(p2);
    }

    /** #216 {@code findByUserId} も同じ行値比較を持つ別SQL。同じ検証が要る。 */
    @Test
    @DisplayName("#216 findByUserId でも同一created_atの2件を取りこぼさない")
    void ユーザー投稿一覧でもタイブレーカーが効く() {
      long me = fixtures.user("alice");
      OffsetDateTime t = instant();
      long p1 = fixtures.postAt(me, "同時刻A", t);
      long p2 = fixtures.postAt(me, "同時刻B", t);

      List<PostRow> page1 = postMapper.findByUserId(me, null, null, 2);
      assertThat(page1).extracting(PostRow::id).containsExactly(p2, p1);

      CursorCodec.Cursor c = cursorOf(page1.get(0));
      List<PostRow> page2 = postMapper.findByUserId(me, c.createdAt(), c.id(), 2);

      assertThat(page2).extracting(PostRow::id).containsExactly(p1);
    }
  }

  @Nested
  @DisplayName("フォロー中タブ")
  class FollowingTab {

    /** #217 FOLLOWING は自分＋フォロー中のみ。他人の投稿が混ざってはいけない。 */
    @Test
    @DisplayName("#217 FOLLOWINGは自分とフォロー中の投稿だけを返す")
    void フォロー中タブの絞り込み() {
      long me = fixtures.user("alice");
      long followee = fixtures.user("bob");
      long stranger = fixtures.user("carol");
      fixtures.follow(me, followee);
      long mine = fixtures.post(me, "自分の投稿");
      long theirs = fixtures.post(followee, "フォロー中の投稿");
      long other = fixtures.post(stranger, "無関係な投稿");

      List<PostRow> rows = postMapper.findTimeline(TimelineTab.FOLLOWING, me, null, null, NO_LIMIT);

      assertThat(rows)
          .extracting(PostRow::id)
          .containsExactlyInAnyOrder(mine, theirs)
          .doesNotContain(other);
    }

    /**
     * #218 誰もフォローしていなくても自分の投稿は出る（F-TL-02、D-11）。
     *
     * <p>投稿直後にこのタブを見て「消えた」と感じさせないための仕様。
     */
    @Test
    @DisplayName("#218 誰もフォローしていなくても自分の投稿は出る")
    void フォロー0でも自分の投稿は出る() {
      long me = fixtures.user("alice");
      long mine = fixtures.post(me, "自分の投稿");

      List<PostRow> rows = postMapper.findTimeline(TimelineTab.FOLLOWING, me, null, null, NO_LIMIT);

      assertThat(rows).extracting(PostRow::id).containsExactly(mine);
    }

    /** #219 ALL では絞り込みが効かず、無関係なユーザーの投稿も含まれる。 */
    @Test
    @DisplayName("#219 ALLでは他人の投稿も含まれる")
    void 全体タブは絞り込まない() {
      long me = fixtures.user("alice");
      long stranger = fixtures.user("carol");
      long other = fixtures.post(stranger, "無関係な投稿");

      List<PostRow> rows = postMapper.findTimeline(TimelineTab.ALL, me, null, null, NO_LIMIT);

      assertThat(rows).extracting(PostRow::id).contains(other);
    }

    /**
     * #220 {@code <if test="tab.name() == 'FOLLOWING'">} はOGNLで {@code tab} のメソッドを呼ぶため、 null
     * を渡すと例外になる。
     *
     * <p>{@code TimelineController} は {@code @RequestParam(defaultValue = "all")} を持つので 本番では null
     * が来ないが、Mapperの契約として「tabは非nullであること」を固定しておく。 MyBatis が例外をくるむため、型は厳密に指定しない。
     */
    @Test
    @DisplayName("#220 tab=null は例外になる（OGNLがnullのメソッドを呼べないため）")
    void タブがnullなら例外() {
      long me = fixtures.user("alice");

      assertThatThrownBy(() -> postMapper.findTimeline(null, me, null, null, NO_LIMIT))
          .isInstanceOf(Exception.class);
    }
  }

  @Nested
  @DisplayName("新着件数")
  class CountNewer {

    /**
     * #221 sinceId より新しい投稿だけを数える（strict greater than）。
     *
     * <p><b>MyBatisの1次キャッシュに注意。</b> {@code localCacheScope} の既定は {@code SESSION} で、
     * 同一トランザクション内で<b>同じ引数の同じクエリを2回呼ぶと、2回目はSQLを発行せず 1回目の結果を返す</b>。データを挟んで同じ問い合わせを繰り返す書き方をすると、
     * SQLではなくキャッシュを検証してしまう。ここでは引数の異なる問い合わせだけを使う。
     */
    @Test
    @DisplayName("#221 sinceId自身は新着に数えない")
    void 新着は厳密に新しいものだけ() {
      long me = fixtures.user("alice");
      long first = fixtures.post(me, "1件目");
      long second = fixtures.post(me, "2件目");
      long third = fixtures.post(me, "3件目");

      // sinceId より大きい id だけを数える。境界（sinceId 自身）は含まない
      assertThat(postMapper.countNewer(TimelineTab.ALL, me, first)).isEqualTo(2);
      assertThat(postMapper.countNewer(TimelineTab.ALL, me, second)).isEqualTo(1);
      assertThat(postMapper.countNewer(TimelineTab.ALL, me, third)).isZero();
    }

    /** #222 論理削除した投稿は新着に数えない（countNewer 側にも activeOnly がある）。 */
    @Test
    @DisplayName("#222 論理削除した投稿は新着に数えない")
    void 削除済みは新着に数えない() {
      long me = fixtures.user("alice");
      long base = fixtures.post(me, "基準");
      long newer = fixtures.post(me, "新着");
      fixtures.softDeletePost(newer);

      assertThat(postMapper.countNewer(TimelineTab.ALL, me, base)).isZero();
    }
  }
}
