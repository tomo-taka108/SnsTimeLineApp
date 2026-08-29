package com.example.snstimeline.user;

import com.example.snstimeline.common.ApiException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.file.FileService;
import com.example.snstimeline.follow.FollowMapper;
import com.example.snstimeline.user.dto.UpdateProfileRequest;
import com.example.snstimeline.user.dto.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * プロフィールの業務ロジック（docs/05_api_design.md #17, #18, #19）。
 *
 * <p>フォロー自体（#21, #22）は {@link com.example.snstimeline.follow.FollowService} が担当する （{@code
 * LikeService} と {@code PostService} を分けたのと同じ理由、単一責任）。 ここでは「フォロー済みかどうか」の参照のみ {@link FollowMapper}
 * を使う。
 */
@Service
public class UserService {

  private final UserMapper userMapper;
  private final FollowMapper followMapper;
  private final FileService fileService;

  public UserService(UserMapper userMapper, FollowMapper followMapper, FileService fileService) {
    this.userMapper = userMapper;
    this.followMapper = followMapper;
    this.fileService = fileService;
  }

  /**
   * #17 プロフィール取得（F-US-01, F-US-02）。
   *
   * <p>postCount / followingCount / followerCount は非正規化カウンタを持たず、都度 {@code COUNT(*)}
   * で算出する（docs/09_decision_log.md D-36）。プロフィール1回の取得につき1ユーザーぶんしか 数えないため、タイムラインのような N 件ぶんの増幅が起きない。
   */
  @Transactional(readOnly = true)
  public UserProfile getProfile(Long meId, Long userId) {
    User user = userMapper.findById(userId).orElseThrow(NotFoundException::new);
    boolean isMe = user.id().equals(meId);
    // isMe の場合は常に false（docs/05_api_design.md 4章 UserProfile.isFollowing の仕様）
    boolean isFollowing = !isMe && followMapper.exists(meId, userId);
    int postCount = userMapper.countPosts(userId);
    int followingCount = followMapper.countFollowing(userId);
    int followerCount = followMapper.countFollowers(userId);
    return UserProfile.of(user, postCount, followingCount, followerCount, isFollowing, isMe);
  }

  /**
   * #19 プロフィール編集（F-US-03）。
   *
   * <p>バリデーションを {@code @Valid} ではなくここで行う理由: {@link UpdateProfileRequest} は 「未送信」と「明示的な
   * null」を区別するため生の {@code JsonNode} で受けており、Bean Validation を適用できないため （{@link UpdateProfileRequest}
   * のJavadoc参照）。
   *
   * <p>{@code email} と {@code username} は変更できない（このDTOで読み取らないため送られても無視される）。
   */
  @Transactional
  public UserProfile updateProfile(Long meId, UpdateProfileRequest request) {
    if (request.isDisplayNameBlank()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    if (request.isDisplayNameTooLong()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    if (request.isBioTooLong()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }
    if (request.isAvatarFileIdInvalid()) {
      throw new ApiException(ErrorCode.VALIDATION_ERROR);
    }

    String displayName = request.hasDisplayName() ? request.displayName() : null;
    boolean bioProvided = request.hasBio();
    String bio = bioProvided && !request.isBioNull() ? request.bio() : null;

    boolean avatarProvided = request.hasAvatarFileId();
    Long avatarFileId =
        avatarProvided && !request.isAvatarFileIdNull() ? request.avatarFileId() : null;
    // null（削除）は自分のファイルかどうかを問わないため検証しない。
    // 存在チェック→404、所有者チェック→403 の順は FileService.assertOwnedBy に集約する（D-14, D-44）
    if (avatarFileId != null) {
      fileService.assertOwnedBy(meId, avatarFileId);
    }

    int affected =
        userMapper.updateProfile(meId, displayName, bioProvided, bio, avatarProvided, avatarFileId);
    if (affected == 0) {
      // 自分自身の操作なので通常起こらないが、論理削除との競合を考慮して念のため判定する
      throw new NotFoundException();
    }
    return getProfile(meId, meId);
  }
}
