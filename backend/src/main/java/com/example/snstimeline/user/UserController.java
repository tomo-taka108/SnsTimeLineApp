package com.example.snstimeline.user;

import com.example.snstimeline.auth.AuthPrincipal;
import com.example.snstimeline.common.CursorPage;
import com.example.snstimeline.common.OffsetPage;
import com.example.snstimeline.follow.FollowService;
import com.example.snstimeline.follow.dto.FollowResponse;
import com.example.snstimeline.post.PostService;
import com.example.snstimeline.post.dto.PostSummary;
import com.example.snstimeline.user.dto.UpdateProfileRequest;
import com.example.snstimeline.user.dto.UserListItem;
import com.example.snstimeline.user.dto.UserProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * ユーザー・プロフィール・検索・フォローAPI（docs/05_api_design.md #17〜#24）。
 *
 * <p>すべて {@code /api/v1/users} 配下に収まるため、{@code CommentController} のように パスをメソッドごとに書き分ける必要はない（クラスレベルの
 * {@code @RequestMapping} で集約する）。
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "ユーザー・フォロー", description = "プロフィールの取得・編集、ユーザー検索、フォロー関係")
public class UserController {

  private final UserService userService;
  private final UserSearchService userSearchService;
  private final PostService postService;
  private final FollowService followService;

  public UserController(
      UserService userService,
      UserSearchService userSearchService,
      PostService postService,
      FollowService followService) {
    this.userService = userService;
    this.userSearchService = userSearchService;
    this.postService = postService;
    this.followService = followService;
  }

  /**
   * #20 ユーザー検索（F-US-05）。<b>唯一のオフセットページネーション</b>（docs/05_api_design.md 2.2）。
   *
   * <p>空パスのマッピング（{@code GET /api/v1/users}）は {@code @GetMapping("/{userId}")} と衝突しない。
   *
   * <p>{@code q} を {@code required = true}（既定）にしているが、未指定時の400は Spring が投げる {@code
   * MissingServletRequestParameterException} 由来になる。空文字・長すぎる値の検証は {@link UserSearchService}
   * が行う。{@code ?page=abc} のような型不一致も既存の {@code MethodArgumentTypeMismatchException}
   * ハンドラが400にするため、ここでの追加実装は不要。
   */
  @GetMapping
  @Operation(
      summary = "ユーザー検索",
      description =
          """
          ユーザー名・表示名で部分一致検索する。**このAPIだけオフセットページネーション**を使う
          （検索結果は件数が有限で、ページ番号を直接指定したいため。タイムラインはカーソル方式）。

          プライバシー保護のため、**メールアドレスは検索対象にもレスポンスにも含まれない**。
          """)
  @ApiResponse(responseCode = "200", description = "検索成功")
  @ApiResponse(responseCode = "400", description = "`q` が未指定・空・長すぎる（VALIDATION_ERROR）")
  public OffsetPage<UserListItem> searchUsers(
      @AuthenticationPrincipal AuthPrincipal principal,
      @Parameter(description = "検索キーワード（必須）", required = true) @RequestParam String q,
      @Parameter(description = "ページ番号。省略時0") @RequestParam(required = false) Integer page,
      @Parameter(description = "1ページあたりの件数。省略時20、最大50") @RequestParam(required = false)
          Integer size) {
    return userSearchService.search(principal.userId(), q, page, size);
  }

  /** #17 プロフィール取得（F-US-01, F-US-02）。 */
  @GetMapping("/{userId}")
  @Operation(
      summary = "プロフィール取得",
      description = "ユーザーのプロフィールを取得する。投稿数・フォロー数・フォロワー数と、自分がフォロー済みかどうかを含む。")
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または退会済み（NOT_FOUND）")
  public UserProfile getProfile(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long userId) {
    return userService.getProfile(principal.userId(), userId);
  }

  /**
   * #19 プロフィール編集（F-US-03）。パスは設計書どおり {@code /users/me} 固定。
   *
   * <p>ボディを生の {@link JsonNode} で受け取る。「フィールド未送信」と「明示的な null」を区別する必要があり {@code record}
   * では表現できないため（{@link UpdateProfileRequest} のJavadoc参照）。
   */
  @PatchMapping("/me")
  @Operation(
      summary = "プロフィール編集",
      description =
          """
          自分のプロフィールを更新する。**送ったフィールドだけが変更される。**

          `bio` / `avatarFileId` / `coverFileId` は、
          **フィールドを送らなければ変更なし、`null` を明示的に送れば削除**となる。
          この2つを区別するため、リクエストは固定の型ではなく任意のJSONとして受け取っている。

          `email` と `username` は変更できない（送られても無視される）。

          | フィールド | 型 | 制約 |
          |---|---|---|
          | `displayName` | string | 1〜50文字。空文字は不可 |
          | `bio` | string / null | 160文字以内。`null` で削除 |
          | `avatarFileId` | number / null | アップロード済みで**自分が所有する**ファイルID。`null` で削除 |
          | `coverFileId` | number / null | 同上 |
          """)
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "更新したいフィールドのみを含むJSON",
      content =
          @Content(
              schema = @Schema(type = "object"),
              examples = {
                @ExampleObject(
                    name = "表示名と自己紹介を変更",
                    value = "{\"displayName\": \"山田太郎\", \"bio\": \"よろしくお願いします\"}"),
                @ExampleObject(name = "自己紹介を削除", value = "{\"bio\": null}"),
                @ExampleObject(name = "アイコン画像を設定", value = "{\"avatarFileId\": 12}")
              }))
  @ApiResponse(responseCode = "200", description = "更新成功")
  @ApiResponse(
      responseCode = "400",
      description = "文字数超過、または `avatarFileId` が数値でない（VALIDATION_ERROR）")
  @ApiResponse(responseCode = "403", description = "他人がアップロードしたファイルを指定した（FORBIDDEN）")
  public UserProfile updateProfile(
      @AuthenticationPrincipal AuthPrincipal principal, @RequestBody JsonNode body) {
    return userService.updateProfile(principal.userId(), new UpdateProfileRequest(body));
  }

  /** #18 ユーザーの投稿一覧（F-US-02, F-TL-03）。 */
  @GetMapping("/{userId}/posts")
  @Operation(summary = "ユーザーの投稿一覧", description = "指定したユーザーの投稿を新しい順に取得する。カーソルベースのページネーション。")
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または退会済み（NOT_FOUND）")
  public CursorPage<PostSummary> getPosts(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long userId,
      @Parameter(description = "取得件数。省略時20、最大50") @RequestParam(required = false) Integer limit,
      @Parameter(description = "前回のレスポンスの `nextCursor`。初回は省略する") @RequestParam(required = false)
          String cursor) {
    return postService.getUserPosts(principal.userId(), userId, limit, cursor);
  }

  /** #21 フォロー（F-FL-01）。冪等。 */
  @PutMapping("/{userId}/follow")
  @Operation(
      summary = "フォロー",
      description = "指定したユーザーをフォローする。**冪等**（すでにフォロー済みでも200を返す）。自分自身はフォローできない。")
  @ApiResponse(responseCode = "200", description = "成功。フォロー後のフォロワー数を返す")
  @ApiResponse(responseCode = "400", description = "自分自身をフォローしようとした（SELF_FOLLOW_NOT_ALLOWED）")
  @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または退会済み（NOT_FOUND）")
  public FollowResponse follow(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long userId) {
    return followService.follow(principal.userId(), userId);
  }

  /** #22 フォロー解除（F-FL-02）。冪等。 */
  @DeleteMapping("/{userId}/follow")
  @Operation(summary = "フォロー解除", description = "フォローを解除する。**冪等**（フォローしていなくても200を返す）。")
  @ApiResponse(responseCode = "200", description = "成功。解除後のフォロワー数を返す")
  @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または退会済み（NOT_FOUND）")
  public FollowResponse unfollow(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long userId) {
    return followService.unfollow(principal.userId(), userId);
  }

  /** #23 フォロー中一覧（F-FL-03）。 */
  @GetMapping("/{userId}/following")
  @Operation(summary = "フォロー中の一覧", description = "指定したユーザーがフォローしているユーザーを取得する。")
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または退会済み（NOT_FOUND）")
  public CursorPage<UserListItem> getFollowing(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long userId,
      @Parameter(description = "取得件数。省略時20、最大50") @RequestParam(required = false) Integer limit,
      @Parameter(description = "前回のレスポンスの `nextCursor`。初回は省略する") @RequestParam(required = false)
          String cursor) {
    return followService.getFollowing(principal.userId(), userId, limit, cursor);
  }

  /** #24 フォロワー一覧（F-FL-04）。 */
  @GetMapping("/{userId}/followers")
  @Operation(summary = "フォロワー一覧", description = "指定したユーザーをフォローしているユーザーを取得する。")
  @ApiResponse(responseCode = "200", description = "取得成功")
  @ApiResponse(responseCode = "404", description = "ユーザーが存在しない、または退会済み（NOT_FOUND）")
  public CursorPage<UserListItem> getFollowers(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long userId,
      @Parameter(description = "取得件数。省略時20、最大50") @RequestParam(required = false) Integer limit,
      @Parameter(description = "前回のレスポンスの `nextCursor`。初回は省略する") @RequestParam(required = false)
          String cursor) {
    return followService.getFollowers(principal.userId(), userId, limit, cursor);
  }
}
