package com.example.snstimeline.post;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.post.dto.CreatePostRequest;
import com.example.snstimeline.post.dto.PostSummary;
import com.example.snstimeline.post.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 投稿API（docs/05_api_design.md #6〜#9）。タイムライン取得は {@link TimelineController} が担当する。 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }

  /** #6 投稿作成。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PostSummary create(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Valid @RequestBody CreatePostRequest request) {
    return postService.create(principal.userId(), request);
  }

  /** #7 投稿詳細取得。 */
  @GetMapping("/{postId}")
  public PostSummary getById(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long postId) {
    return postService.getById(principal.userId(), postId);
  }

  /** #8 投稿編集（docs/09_decision_log.md D-30 によりMVPへ前倒し）。 */
  @PatchMapping("/{postId}")
  public PostSummary update(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long postId,
      @Valid @RequestBody UpdatePostRequest request) {
    return postService.update(principal.userId(), postId, request);
  }

  /** #9 投稿削除。 */
  @DeleteMapping("/{postId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long postId) {
    postService.delete(principal.userId(), postId);
  }
}
