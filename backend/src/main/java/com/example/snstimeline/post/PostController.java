package com.example.snstimeline.post;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.post.dto.CreatePostRequest;
import com.example.snstimeline.post.dto.LikeResponse;
import com.example.snstimeline.post.dto.PostSummary;
import com.example.snstimeline.post.dto.UpdatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 投稿API（docs/05_api_design.md #6〜#9, #14, #15）。タイムライン取得は {@link TimelineController} が担当する。 */
@RestController
@RequestMapping("/api/v1/posts")
@Tag(name = "投稿", description = "投稿の作成・取得・編集・削除といいね")
public class PostController {

  private final PostService postService;
  private final LikeService likeService;

  public PostController(PostService postService, LikeService likeService) {
    this.postService = postService;
    this.likeService = likeService;
  }

  /** #6 投稿作成。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "投稿作成", description = "本文（最大280文字）と任意の画像で投稿を作成する。")
  @ApiResponse(responseCode = "201", description = "作成成功")
  @ApiResponse(responseCode = "400", description = "本文が空、または280文字を超えている（VALIDATION_ERROR）")
  public PostSummary create(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody CreatePostRequest request) {
    return postService.create(principal.userId(), request);
  }

  /** #7 投稿詳細取得。 */
  @GetMapping("/{postId}")
  @Operation(summary = "投稿詳細取得", description = "投稿1件を取得する。いいね数・コメント数と、自分がいいね済みかどうかを含む。")
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public PostSummary getById(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long postId) {
    return postService.getById(principal.userId(), postId);
  }

  /** #8 投稿編集（docs/09_decision_log.md D-30 によりMVPへ前倒し）。 */
  @PatchMapping("/{postId}")
  @Operation(summary = "投稿編集", description = "自分の投稿の本文を編集する。他人の投稿は編集できない。")
  @ApiResponse(responseCode = "200", description = "編集成功")
  @ApiResponse(responseCode = "403", description = "他人の投稿を編集しようとした（FORBIDDEN）")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public PostSummary update(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long postId,
      @Valid @RequestBody UpdatePostRequest request) {
    return postService.update(principal.userId(), postId, request);
  }

  /** #9 投稿削除。 */
  @DeleteMapping("/{postId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "投稿削除", description = "自分の投稿を削除する（論理削除）。他人の投稿は削除できない。")
  @ApiResponse(responseCode = "204", description = "削除成功（レスポンスボディなし）")
  @ApiResponse(responseCode = "403", description = "他人の投稿を削除しようとした（FORBIDDEN）")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long postId) {
    postService.delete(principal.userId(), postId);
  }

  /** #14 いいね（F-LK-01）。冪等。 */
  @PutMapping("/{postId}/like")
  @Operation(summary = "いいね", description = "投稿にいいねを付ける。**冪等**（すでにいいね済みでも200を返し、カウンタは増えない）。")
  @ApiResponse(responseCode = "200", description = "成功。いいね後の件数を返す")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public LikeResponse like(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long postId) {
    return likeService.like(principal.userId(), postId);
  }

  /** #15 いいね解除（F-LK-02）。冪等。 */
  @DeleteMapping("/{postId}/like")
  @Operation(summary = "いいね解除", description = "いいねを取り消す。**冪等**（いいねしていなくても200を返し、カウンタは減らない）。")
  @ApiResponse(responseCode = "200", description = "成功。解除後の件数を返す")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public LikeResponse unlike(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long postId) {
    return likeService.unlike(principal.userId(), postId);
  }
}
