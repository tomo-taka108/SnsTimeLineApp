package com.example.snstimeline.post;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.post.dto.NewCountResponse;
import com.example.snstimeline.post.dto.PostSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** タイムラインAPI（docs/05_api_design.md #5、および独自追加の #29）。 */
@RestController
@RequestMapping("/api/v1/timeline")
@Tag(name = "タイムライン", description = "全体／フォロー中のタイムラインと新着件数")
public class TimelineController {

  private final PostService postService;

  public TimelineController(PostService postService) {
    this.postService = postService;
  }

  /** #5 タイムライン取得（F-TL-01, F-TL-02, F-TL-03）。 */
  @GetMapping
  @Operation(
      summary = "タイムライン取得",
      description =
          """
          投稿を新しい順に取得する。**カーソルベースのページネーション**を使う。

          初回は `cursor` を省略し、2ページ目以降はレスポンスの `nextCursor` をそのまま渡す。
          `hasNext` が false になったら終端。

          カーソルは不透明な文字列であり、**クライアントは中身を解釈してはならない**。
          """)
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "400", description = "カーソルが不正、または limit が範囲外（VALIDATION_ERROR）")
  public CursorPage<PostSummary> getTimeline(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Parameter(description = "`all`（全体）または `following`（フォロー中）")
          @RequestParam(defaultValue = "all")
          String tab,
      @Parameter(description = "取得件数。省略時20、最大50") @RequestParam(required = false) Integer limit,
      @Parameter(description = "前回のレスポンスの `nextCursor`。初回は省略する") @RequestParam(required = false)
          String cursor) {
    return postService.getTimeline(principal.userId(), TimelineTab.from(tab), limit, cursor);
  }

  /**
   * #29 新着投稿の件数（本プロジェクト独自。設計書#1〜#28には無い、docs/09_decision_log.md D-31）。
   *
   * <p>{@code sinceId} はクライアントが表示している先頭投稿の id。カーソルは「末尾」しか 表さないため使えず、id
   * がBIGSERIALの単調増加であることを利用して新着判定する。
   */
  @GetMapping("/new-count")
  @Operation(
      summary = "新着投稿の件数",
      description =
          """
          指定した投稿より新しい投稿が何件あるかを返す。「新着◯件」のバッジ表示に使う。

          `sinceId` にはクライアントが表示している**先頭**の投稿IDを渡す。
          カーソルは「末尾」しか表さないため使えず、IDが単調増加であることを利用して判定している。
          """)
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "400", description = "`sinceId` が未指定または数値でない（VALIDATION_ERROR）")
  public NewCountResponse getNewCount(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Parameter(description = "`all`（全体）または `following`（フォロー中）")
          @RequestParam(defaultValue = "all")
          String tab,
      @Parameter(description = "画面に表示している先頭投稿のID") @RequestParam Long sinceId) {
    return new NewCountResponse(
        postService.countNewer(principal.userId(), TimelineTab.from(tab), sinceId));
  }
}
