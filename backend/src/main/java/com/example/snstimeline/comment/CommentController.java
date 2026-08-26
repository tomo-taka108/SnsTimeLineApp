package com.example.snstimeline.comment;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.comment.dto.CommentSummary;
import com.example.snstimeline.comment.dto.CreateCommentRequest;
import com.example.snstimeline.comment.dto.CreateCommentResponse;
import com.example.snstimeline.comment.dto.DeleteCommentResponse;
import com.example.snstimeline.common.CursorPage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * コメントAPI（docs/05_api_design.md #10, #11, #13）。
 *
 * <p>URLが {@code /posts/{postId}/comments} と {@code /comments/{commentId}} の2系統に分かれる
 * （設計書どおり）ため、クラスに {@code @RequestMapping} を固定せず各メソッドにフルパスを書く。
 *
 * <p>コメント編集（#12）はPhase2のため実装しない。
 */
@RestController
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  /** #10 コメント一覧。 */
  @GetMapping("/api/v1/posts/{postId}/comments")
  public CursorPage<CommentSummary> getComments(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long postId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return commentService.getComments(principal.userId(), postId, limit, cursor);
  }

  /** #11 コメント投稿。 */
  @PostMapping("/api/v1/posts/{postId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateCommentResponse create(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long postId,
      @Valid @RequestBody CreateCommentRequest request) {
    return commentService.create(principal.userId(), postId, request);
  }

  /** #13 コメント削除。パスに postId を含まない（設計書どおり）。 */
  @DeleteMapping("/api/v1/comments/{commentId}")
  public DeleteCommentResponse delete(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long commentId) {
    return commentService.delete(principal.userId(), commentId);
  }
}
