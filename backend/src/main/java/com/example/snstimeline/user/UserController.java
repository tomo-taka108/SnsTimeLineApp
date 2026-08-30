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
  public OffsetPage<UserListItem> searchUsers(
      @AuthenticationPrincipal AuthPrincipal principal,
      @RequestParam String q,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return userSearchService.search(principal.userId(), q, page, size);
  }

  /** #17 プロフィール取得（F-US-01, F-US-02）。 */
  @GetMapping("/{userId}")
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
  public UserProfile updateProfile(
      @AuthenticationPrincipal AuthPrincipal principal, @RequestBody JsonNode body) {
    return userService.updateProfile(principal.userId(), new UpdateProfileRequest(body));
  }

  /** #18 ユーザーの投稿一覧（F-US-02, F-TL-03）。 */
  @GetMapping("/{userId}/posts")
  public CursorPage<PostSummary> getPosts(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long userId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return postService.getUserPosts(principal.userId(), userId, limit, cursor);
  }

  /** #21 フォロー（F-FL-01）。冪等。 */
  @PutMapping("/{userId}/follow")
  public FollowResponse follow(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long userId) {
    return followService.follow(principal.userId(), userId);
  }

  /** #22 フォロー解除（F-FL-02）。冪等。 */
  @DeleteMapping("/{userId}/follow")
  public FollowResponse unfollow(
      @AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long userId) {
    return followService.unfollow(principal.userId(), userId);
  }

  /** #23 フォロー中一覧（F-FL-03）。 */
  @GetMapping("/{userId}/following")
  public CursorPage<UserListItem> getFollowing(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long userId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return followService.getFollowing(principal.userId(), userId, limit, cursor);
  }

  /** #24 フォロワー一覧（F-FL-04）。 */
  @GetMapping("/{userId}/followers")
  public CursorPage<UserListItem> getFollowers(
      @AuthenticationPrincipal AuthPrincipal principal,
      @PathVariable Long userId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    return followService.getFollowers(principal.userId(), userId, limit, cursor);
  }
}
