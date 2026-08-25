package com.example.snstimeline.post;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.post.dto.NewCountResponse;
import com.example.snstimeline.post.dto.PostSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** タイムラインAPI（docs/05_api_design.md #5、および独自追加の #29）。 */
@RestController
@RequestMapping("/api/v1/timeline")
public class TimelineController {

  private final PostService postService;

  public TimelineController(PostService postService) {
    this.postService = postService;
  }

  /** #5 タイムライン取得（F-TL-01, F-TL-02, F-TL-03）。 */
  @GetMapping
  public CursorPage<PostSummary> getTimeline(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(defaultValue = "all") String tab,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return postService.getTimeline(principal.userId(), TimelineTab.from(tab), limit, cursor);
  }

  /**
   * #29 新着投稿の件数（本プロジェクト独自。設計書#1〜#28には無い、docs/09_decision_log.md D-31）。
   *
   * <p>{@code sinceId} はクライアントが表示している先頭投稿の id。カーソルは「末尾」しか 表さないため使えず、id
   * がBIGSERIALの単調増加であることを利用して新着判定する。
   */
  @GetMapping("/new-count")
  public NewCountResponse getNewCount(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam(defaultValue = "all") String tab,
      @RequestParam Long sinceId) {
    return new NewCountResponse(
        postService.countNewer(principal.userId(), TimelineTab.from(tab), sinceId));
  }
}
