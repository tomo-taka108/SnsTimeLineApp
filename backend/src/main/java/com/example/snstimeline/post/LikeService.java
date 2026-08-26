package com.example.snstimeline.post;

import com.example.snstimeline.common.NotFoundException;
import com.example.snstimeline.post.dto.LikeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * いいねの業務ロジック（docs/05_api_design.md #14, #15）。
 *
 * <p>{@code PostService} とは分離する。将来のいいねしたユーザー一覧（F-LK-04, API #16、Phase2）など、
 * いいね単体のユースケースが増える見込みがあり、単一責任にしておくため。
 *
 * <p>いいねには「所有者チェック」に相当する概念が無い（誰でも他人の投稿にいいねできる）。 D-14の2段階認可（存在チェック→404、所有者チェック→403）はそのまま適用できず、
 * 「存在チェックのみ→404」で足りる。
 */
@Service
public class LikeService {

  private final PostMapper postMapper;
  private final LikeMapper likeMapper;

  public LikeService(PostMapper postMapper, LikeMapper likeMapper) {
    this.postMapper = postMapper;
    this.likeMapper = likeMapper;
  }

  /**
   * #14 いいね（F-LK-01）。冪等: 既にいいね済みでも {@code 200 OK} を返し、カウンタは増やさない。
   *
   * <p>UNIQUE制約違反（{@link DuplicateKeyException}）を捕捉して実現する。 {@code GlobalExceptionHandler}
   * の保険ハンドラは認証機能専用（{@code EMAIL_ALREADY_EXISTS} 固定）のため、 ここで個別に捕捉する（docs/09_decision_log.md D-34）。
   */
  @Transactional
  public LikeResponse like(Long meId, Long postId) {
    postMapper.findById(postId).orElseThrow(NotFoundException::new);

    try {
      likeMapper.insert(postId, meId);
      likeMapper.incrementLikeCount(postId);
    } catch (DuplicateKeyException e) {
      // 既にいいね済み。カウンタは更新せず200を返す（冪等性）
    }
    return new LikeResponse(likeMapper.findLikeCount(postId), true);
  }

  /** #15 いいね解除（F-LK-02）。冪等: いいねしていない状態で呼ばれても {@code 200 OK}。カウンタは減らさない。 */
  @Transactional
  public LikeResponse unlike(Long meId, Long postId) {
    postMapper.findById(postId).orElseThrow(NotFoundException::new);

    int affected = likeMapper.delete(postId, meId);
    if (affected > 0) {
      likeMapper.decrementLikeCount(postId);
    }
    return new LikeResponse(likeMapper.findLikeCount(postId), false);
  }
}
