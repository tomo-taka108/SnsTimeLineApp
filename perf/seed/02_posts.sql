-- 負荷試験用シードデータ ② 投稿10,000件（docs/13_performance_test.md）
--
-- 想定データ量は docs/06_non_functional.md 1.1 に従う（投稿数 10,000件）。
--
-- 【なぜ10,000件必要か】
-- データが少ないとプランナが「全件走査した方が速い」と判断し、
-- idx_posts_timeline を使ってくれない（06 の 1.4 に明記）。
-- インデックスが効いているかを検証するには、まず量が要る。
--
-- 【created_at の分布が設計の要】
-- 3種類を意図的に混ぜている。
--   ① 過去90日にばらけた値（約9,900件）… idx_posts_timeline の検証
--   ② 同一 created_at の塊（約80件）    … D-33 の退行検知
--   ③ 直近の数件                        … new-count API 用
--
-- ②が重要。カーソルは (created_at, id) の行値比較でページを繰るため、
-- 同一時刻の投稿が無いと「タイブレーカーが壊れていても気づけない」。
-- D-33 は実際にこのバグ（秒精度で同一秒内の投稿を取りこぼす）を踏んでいる。
--
-- 【罠】posts.created_at の DEFAULT now() は**トランザクション開始時刻**を返す。
-- 単一の INSERT ... SELECT で入れると全件が同じ時刻になってしまうため、
-- ①では created_at を必ず明示指定する。

SELECT setseed(0.42);

-- ① 過去90日にばらけた投稿（9,900件）
-- マイクロ秒精度で散らす。ORDER BY で id と created_at の順序を揃えておくと
-- カーソルの挙動が読みやすくなる（本番は必ずしも一致しないが、検証では有利）。
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT
    (n % 100) + 1,
    '負荷試験の投稿です。連番 ' || n || ' / タイムライン取得の検証に使います。',
    ts,
    ts
FROM (
    SELECT
        n,
        date_trunc('microseconds',
            now() - (INTERVAL '90 days') + (random() * INTERVAL '90 days')
        ) AS ts
    FROM generate_series(1, 9900) AS n
) AS src;

-- ② 同一 created_at の塊（20件 × 4箇所 = 80件）
-- D-33（カーソルのマイクロ秒精度）の退行検知用。
-- 同じマイクロ秒値を持つ投稿が20件あるので、タイブレーカーが効いていないと
-- ページ跨ぎで重複・欠落が起きる。
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT
    (n % 100) + 1,
    '[TIE] 同一時刻の投稿です。塊 ' || grp || ' / 連番 ' || n,
    ts,
    ts
FROM (
    SELECT
        grp,
        n,
        -- 塊ごとに1つの時刻を固定する（date_trunc で必ずマイクロ秒に丸める）
        date_trunc('microseconds', now() - (INTERVAL '10 days') + (grp * INTERVAL '1 day')) AS ts
    FROM generate_series(1, 4) AS grp
    CROSS JOIN generate_series(1, 20) AS n
) AS src;

-- ③ ホットスポット投稿（いいね100件を集中させる対象。03_social.sql で付ける）
-- body の先頭を [HOTSPOT] にして run-perf.sh から id を引けるようにしている。
INSERT INTO posts (user_id, body, created_at, updated_at)
VALUES (
    1,
    '[HOTSPOT] いいねが集中する投稿です。行ロック競合の検証に使います。',
    date_trunc('microseconds', now() - INTERVAL '5 days'),
    date_trunc('microseconds', now() - INTERVAL '5 days')
);

-- ④ コメント100件の投稿（コメント一覧の昇順カーソル検証用）
INSERT INTO posts (user_id, body, created_at, updated_at)
VALUES (
    2,
    '[COMMENTS] コメントが100件ぶら下がる投稿です。昇順カーソルの検証に使います。',
    date_trunc('microseconds', now() - INTERVAL '4 days'),
    date_trunc('microseconds', now() - INTERVAL '4 days')
);

-- ⑤ 直近の投稿（new-count API が 0 以外を返す状態を作る）
INSERT INTO posts (user_id, body, created_at, updated_at)
SELECT
    (n % 100) + 1,
    '[RECENT] 直近の投稿です。新着件数バナーの検証に使います。連番 ' || n,
    ts,
    ts
FROM (
    SELECT n, date_trunc('microseconds', now() - (n * INTERVAL '1 minute')) AS ts
    FROM generate_series(1, 18) AS n
) AS src;

-- 論理削除された投稿も混ぜる（deleted_at IS NULL の絞り込みが効いているかの検証。D-02）
-- タイムラインに出てはいけない。部分インデックス idx_posts_timeline の対象外でもある。
UPDATE posts
SET deleted_at = now()
WHERE id IN (
    SELECT id FROM posts
    WHERE body LIKE '負荷試験の投稿です。%'
    ORDER BY id
    LIMIT 50
);
