-- 非正規化カウンタの整合性検証（docs/04_data_model.md 3.1 Appendix）
--
-- posts.like_count / comment_count が実データと一致しているかを確認する。
-- **0行が返れば整合している。** 1行でも返ればズレている。
--
-- 【いつ使うか】
--   1. シード投入直後 … 最初からズレていないことの確認
--   2. 負荷試験の直後 … D-01 のロストアップデートが起きていないことの確認
--
-- 【順序の罠】必ず TRUNCATE の**前**に実行すること。
-- 掃除してから検証しても全テーブルが空なので、当然0行になり意味がない。
-- run-perf.sh は k6 実行の直後・後片付けの直前にこれを呼んでいる。
--
-- 【なぜ重要か】いいねは「同一トランザクション内で SQL 側の相対更新」
-- （UPDATE posts SET like_count = like_count + 1）で更新している。
-- Java 側で read-modify-write すると同時実行でロストアップデートが起きる。
-- 同一投稿に100並列でいいねを打ったとき、ここがズレなければ D-01 の
-- 実装が正しいことの実証になる。

\t on

SELECT
    'post_id=' || p.id
        || ' like_count=' || p.like_count
        || ' actual_likes=' || (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id)
        || ' comment_count=' || p.comment_count
        || ' actual_comments=' || (SELECT COUNT(*) FROM comments c
                                   WHERE c.post_id = p.id AND c.deleted_at IS NULL)
FROM posts p
WHERE p.like_count <> (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id)
   OR p.comment_count <> (SELECT COUNT(*) FROM comments c
                          WHERE c.post_id = p.id AND c.deleted_at IS NULL)
ORDER BY p.id
LIMIT 20;

\t off
