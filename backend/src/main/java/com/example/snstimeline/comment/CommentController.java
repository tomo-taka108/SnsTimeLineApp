package com.example.snstimeline.comment;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.comment.dto.CommentSummary;
import com.example.snstimeline.comment.dto.CreateCommentRequest;
import com.example.snstimeline.comment.dto.CreateCommentResponse;
import com.example.snstimeline.comment.dto.DeleteCommentResponse;
import com.example.snstimeline.comment.dto.UpdateCommentRequest;
import com.example.snstimeline.common.CursorPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * コメントAPI（docs/05_api_design.md #10, #11, #12, #13）。
 *
 * <p>URLが {@code /posts/{postId}/comments} と {@code /comments/{commentId}} の2系統に分かれる
 * （設計書どおり）ため、クラスに {@code @RequestMapping} を固定せず各メソッドにフルパスを書く。
 */
@RestController
@Tag(name = "コメント", description = "投稿へのコメントの一覧・投稿・編集・削除")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  /** #10 コメント一覧。 */
  @GetMapping("/api/v1/posts/{postId}/comments")
  @Operation(summary = "コメント一覧", description = "投稿に付いたコメントを古い順に取得する。タイムラインと同じカーソルベースのページネーションを使う。")
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public CursorPage<CommentSummary> getComments(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long postId,
      @Parameter(description = "取得件数。省略時20、最大50") @RequestParam(required = false) Integer limit,
      @Parameter(description = "前回のレスポンスの `nextCursor`。初回は省略する") @RequestParam(required = false)
          String cursor) {
    return commentService.getComments(principal.userId(), postId, limit, cursor);
  }

  /** #11 コメント投稿。 */
  @PostMapping("/api/v1/posts/{postId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "コメント投稿",
      description = "投稿にコメントを追加する。レスポンスには追加後のコメント数も含む（投稿側のカウンタと同一トランザクションで更新される）。")
  @ApiResponse(responseCode = "201", description = "投稿成功")
  @ApiResponse(responseCode = "400", description = "本文が空、または文字数超過（VALIDATION_ERROR）")
  @ApiResponse(responseCode = "404", description = "投稿が存在しない、または削除済み（NOT_FOUND）")
  public CreateCommentResponse create(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long postId,
      @Valid @RequestBody CreateCommentRequest request) {
    return commentService.create(principal.userId(), postId, request);
  }

  /** #13 コメント削除。パスに postId を含まない（設計書どおり）。 */
  @DeleteMapping("/api/v1/comments/{commentId}")
  @Operation(summary = "コメント削除", description = "自分のコメントを削除する（論理削除）。レスポンスには削除後のコメント数を含む。")
  @ApiResponse(responseCode = "200", description = "削除成功")
  @ApiResponse(responseCode = "403", description = "他人のコメントを削除しようとした（FORBIDDEN）")
  @ApiResponse(responseCode = "404", description = "コメントが存在しない、または削除済み（NOT_FOUND）")
  public DeleteCommentResponse delete(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long commentId) {
    return commentService.delete(principal.userId(), commentId);
  }

  /**
   * #12 コメント編集（F-CM-03、docs/09_decision_log.md D-51 によりMVPへ前倒し）。パスに postId を含まない（{@link #delete}
   * と同じ）。
   */
  @PatchMapping("/api/v1/comments/{commentId}")
  @Operation(summary = "コメント編集", description = "自分のコメントの本文を編集する。他人のコメントは編集できない。")
  @ApiResponse(responseCode = "200", description = "編集成功")
  @ApiResponse(responseCode = "403", description = "他人のコメントを編集しようとした（FORBIDDEN）")
  @ApiResponse(responseCode = "404", description = "コメントが存在しない、または削除済み（NOT_FOUND）")
  public CommentSummary update(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long commentId,
      @Valid @RequestBody UpdateCommentRequest request) {
    return commentService.update(principal.userId(), commentId, request);
  }
}
