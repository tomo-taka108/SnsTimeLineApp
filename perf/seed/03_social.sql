-- 負荷試験用シードデータ ③ フォロー・いいね・コメント（docs/13_performance_test.md）
--
-- 想定データ量は docs/06_non_functional.md 1.1 に従う。
--   1ユーザーあたりのフォロー数: 最大50人
--   1投稿あたりのコメント数    : 平均5件、最大100件
--   1投稿あたりのいいね数      : 平均10件、最大100件
--
-- 【最重要】いいね・コメントを入れたら、必ず posts.like_count /
-- comment_count も同じだけ更新すること（D-01 の非正規化カウンタ）。
-- これを忘れるとシード投入の時点でカウンタがズレ、
-- docs/04_data_model.md 3.1 の整合検証SQLが即NGになる。
-- そうなると「負荷試験でロストアップデートが起きたのか、
-- 最初からズレていたのか」が区別できなくなり、③のシナリオが意味を失う。
--
-- 【Phase 1（timeline.ts）での使用状況】docs/13_performance_test.md 3.5 に詳細。
-- 通常のいいね・コメントは likeCount/commentCount という合計値として使われるが、
-- 以下は現時点では k6 シナリオから未使用（Phase 2 の先行投資）:
--   - フォロー関係（following タブ試験用）
--   - ホットスポット投稿への集中いいね100件（同一投稿への集中いいね試験用）
--   - [COMMENTS] 投稿への100件コメント（コメント一覧試験用。未計画）

SELECT setseed(0.42);

-- ---- フォロー -------------------------------------------------------------
-- 1ユーザーあたり最大50人。決定論的に割り当てる。
-- 自己フォローは作らない（アプリ側で 400 SELF_FOLLOW_NOT_ALLOWED になる関係）。
INSERT INTO follows (follower_id, followee_id, created_at)
SELECT DISTINCT
    f.follower_id,
    f.followee_id,
    now() - INTERVAL '60 days'
FROM (
    SELECT
        u AS follower_id,
        ((u + k * 7 - 1) % 100) + 1 AS followee_id
    FROM generate_series(1, 100) AS u
    CROSS JOIN generate_series(1, 50) AS k
) AS f
WHERE f.follower_id <> f.followee_id
ON CONFLICT DO NOTHING;

-- ---- いいね（通常の投稿：平均10件）----------------------------------------
-- 論理削除されていない通常投稿に対し、0〜20件のいいねを散らす（平均10件）。
INSERT INTO likes (post_id, user_id, created_at)
SELECT DISTINCT
    l.post_id,
    l.user_id,
    now() - INTERVAL '30 days'
FROM (
    SELECT
        p.id AS post_id,
        ((p.id * 13 + k * 17 - 1) % 100) + 1 AS user_id
    FROM posts p
    CROSS JOIN generate_series(1, 20) AS k
    WHERE p.deleted_at IS NULL
      AND p.body NOT LIKE '[HOTSPOT]%'
      AND (p.id + k) % 2 = 0
) AS l
ON CONFLICT DO NOTHING;

-- ---- いいね（ホットスポット投稿：100件を集中）------------------------------
-- 06 の 1.1 の「1投稿あたりのいいね最大100件」の上限ケース。
-- 同一投稿への集中いいね（行ロック競合）シナリオの対象になる。
INSERT INTO likes (post_id, user_id, created_at)
SELECT
    (SELECT id FROM posts WHERE body LIKE '[HOTSPOT]%' ORDER BY id LIMIT 1),
    n,
    now() - INTERVAL '5 days'
FROM generate_series(1, 100) AS n
ON CONFLICT DO NOTHING;

-- ---- コメント（通常の投稿：平均5件）----------------------------------------
INSERT INTO comments (post_id, user_id, body, created_at, updated_at)
SELECT
    c.post_id,
    c.user_id,
    '負荷試験のコメントです。投稿 ' || c.post_id || ' への ' || c.k || ' 件目。',
    c.ts,
    c.ts
FROM (
    SELECT
        p.id AS post_id,
        ((p.id * 7 + k * 11 - 1) % 100) + 1 AS user_id,
        k,
        date_trunc('microseconds', p.created_at + (k * INTERVAL '1 minute')) AS ts
    FROM posts p
    CROSS JOIN generate_series(1, 10) AS k
    WHERE p.deleted_at IS NULL
      AND p.body NOT LIKE '[COMMENTS]%'
      AND (p.id + k) % 2 = 0
) AS c;

-- ---- コメント（[COMMENTS] 投稿：100件）------------------------------------
-- 上限ケース。コメント一覧は**昇順**（古い順）カーソルなので、
-- created_at を1分ずつ確実にずらして順序を作る。
INSERT INTO comments (post_id, user_id, body, created_at, updated_at)
SELECT
    (SELECT id FROM posts WHERE body LIKE '[COMMENTS]%' ORDER BY id LIMIT 1),
    (n % 100) + 1,
    '上限ケースのコメントです。' || n || ' 件目。',
    ts,
    ts
FROM (
    SELECT n, date_trunc('microseconds', now() - INTERVAL '4 days' + (n * INTERVAL '1 minute')) AS ts
    FROM generate_series(1, 100) AS n
) AS src;

-- ---- 非正規化カウンタの同期（D-01）----------------------------------------
-- ここまでの INSERT は likes / comments テーブルにしか入れていない。
-- posts.like_count / comment_count を実データに合わせる。
--
-- 通常のアプリ動作では「登録とカウンタ更新を同一トランザクションで、
-- SQL側で相対更新」するが、シードは一括投入なので最後にまとめて同期する。
-- 結果として満たすべき不変条件は同じ:
--   posts.like_count    = COUNT(likes)
--   posts.comment_count = COUNT(comments WHERE deleted_at IS NULL)
UPDATE posts p
SET like_count = COALESCE(l.cnt, 0)
FROM (
    SELECT post_id, COUNT(*) AS cnt FROM likes GROUP BY post_id
) AS l
WHERE p.id = l.post_id;

UPDATE posts p
SET comment_count = COALESCE(c.cnt, 0)
FROM (
    SELECT post_id, COUNT(*) AS cnt
    FROM comments
    WHERE deleted_at IS NULL
    GROUP BY post_id
) AS c
WHERE p.id = c.post_id;

-- 統計情報を更新する。投入直後はプランナが古い統計を持っているため、
-- ANALYZE しないと EXPLAIN の結果が実態と食い違う。
ANALYZE users;
ANALYZE posts;
ANALYZE likes;
ANALYZE comments;
ANALYZE follows;
