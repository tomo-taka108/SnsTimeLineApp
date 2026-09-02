-- 全テーブルを空にする。
--
-- @Transactional によるロールバックが使えないテスト専用（docs/09_decision_log.md D-56）。
-- RefreshTokenRevoker は REQUIRES_NEW で独立したトランザクションをコミットするため、
-- テストをロールバックさせても失効の効果が残る（あるいは親の未コミット行が内側から見えず
-- テストが偽陰性になる）。そのテストだけ非トランザクションで動かし、これで後片付けする。
--
-- flyway_schema_history は絶対に含めない。消すとマイグレーションが再実行される。
TRUNCATE TABLE post_images, likes, comments, follows, refresh_tokens,
               posts, stored_files, users
  RESTART IDENTITY CASCADE;
