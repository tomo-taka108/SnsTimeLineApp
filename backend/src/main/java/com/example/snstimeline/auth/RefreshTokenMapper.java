package com.example.snstimeline.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * refresh_tokens テーブルへのアクセス。SQLは resources/mapper/RefreshTokenMapper.xml に集約する。
 *
 * <p>このテーブルは論理削除（deleted_at）ではなく {@code revoked_at} / {@code used_at} で状態を持つため、 users のような {@code
 * deleted_at IS NULL} の付与ルール（D-25）は適用しない。 代わりに「取得は必ず1件、状態判定はサービス層」という方針にする。
 */
@Mapper
public interface RefreshTokenMapper {

  /**
   * ハッシュで1件引く。
   *
   * <p><b>使用済み・失効済みも含めて引く。</b> 使用済みトークンの再提示を「盗用」として検知するには、 除外せずに取得して状態を見る必要があるため（D-29）。
   */
  Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

  int insert(@Param("token") RefreshToken token);

  /**
   * 未使用の1件を使用済みにする。
   *
   * <p><b>更新件数を返すことが重要。</b> {@code used_at IS NULL} を条件に含めているため、 同時に2つのリクエストが同じトークンでリフレッシュしても、
   * 更新できるのは1つだけになる（DBの行ロックで直列化される）。 0件なら競合に負けたか既に使用済みであり、呼び出し側は失敗として扱う。
   */
  int markUsed(@Param("id") Long id, @Param("now") OffsetDateTime now);

  /** ファミリーごと失効させる（盗用検知時）。 */
  int revokeFamily(@Param("familyId") UUID familyId, @Param("now") OffsetDateTime now);

  /** ユーザーの有効なトークンをすべて失効させる（ログアウト）。 */
  int revokeAllByUserId(@Param("userId") Long userId, @Param("now") OffsetDateTime now);
}
