package com.example.snstimeline.auth;

import com.example.snstimeline.auth.dto.AuthResponse;
import com.example.snstimeline.auth.dto.LoginRequest;
import com.example.snstimeline.auth.dto.SignupRequest;
import com.example.snstimeline.common.ConflictException;
import com.example.snstimeline.common.ErrorCode;
import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.common.UnauthorizedException;
import com.example.snstimeline.user.User;
import com.example.snstimeline.user.UserMapper;
import com.example.snstimeline.user.dto.UserSummary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 認証の業務ロジック。トランザクション境界はこの層に置く（docs/07_architecture.md 2.1）。 */
@Service
public class AuthService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  public AuthService(
      UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  /** #1 新規登録。登録と同時にJWTを発行する（F-AU-01）。 */
  @Transactional
  public AuthResponse signup(SignupRequest request) {
    // 事前チェック。分かりやすいエラーコードを返すため。
    // ただしこれだけでは同時登録の競合（TOCTOU）を防げないので、後段で例外も捕捉する。
    if (userMapper.existsByEmailIncludingDeleted(request.email())) {
      throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
    if (userMapper.existsByUsernameIncludingDeleted(request.username())) {
      throw new ConflictException(ErrorCode.USERNAME_ALREADY_EXISTS);
    }

    User user =
        User.forSignup(
            request.email(),
            passwordEncoder.encode(request.password()),
            request.username(),
            request.displayName());

    Long userId;
    try {
      // MyBatis は insert 呼び出しで即座にSQLを発行するため、JPA の saveAndFlush に
      // 相当する配慮は不要
      userId = userMapper.insert(user);
    } catch (DuplicateKeyException e) {
      // 事前チェックをすり抜けた同時登録の競合
      throw toConflict(e);
    }

    String token = jwtTokenProvider.createToken(userId);
    return new AuthResponse(
        token, new UserSummary(userId, user.username(), user.displayName(), null));
  }

  /** #2 ログイン（F-AU-02）。 */
  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    // メールが存在しない場合とパスワードが違う場合で、レスポンスを区別しない。
    // アカウントの存在を推測させないため（docs/06_non_functional.md 3.1）。
    User user =
        userMapper
            .findByEmail(request.email())
            .filter(u -> passwordEncoder.matches(request.password(), u.passwordHash()))
            .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS));

    return new AuthResponse(jwtTokenProvider.createToken(user.id()), UserSummary.from(user));
  }

  /** #3 現在のユーザー情報（F-AU-04）。 */
  @Transactional(readOnly = true)
  public UserSummary getMe(Long userId) {
    // 論理削除済みなら findById が空を返すので 404 になる
    return userMapper.findById(userId).map(UserSummary::from).orElseThrow(NotFoundException::new);
  }

  /** 制約名から、どちらの一意制約に違反したかを判定する。 */
  private ConflictException toConflict(DuplicateKeyException e) {
    String message = String.valueOf(e.getMostSpecificCause().getMessage());
    if (message.contains("uq_users_username")) {
      return new ConflictException(ErrorCode.USERNAME_ALREADY_EXISTS);
    }
    // uq_users_email、および判別不能時のフォールバック
    return new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
  }
}
