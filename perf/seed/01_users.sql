-- 負荷試験用シードデータ ① ユーザー100人（docs/13_performance_test.md）
--
-- 想定データ量は docs/06_non_functional.md 1.1 に従う（ユーザー数 100人）。
--
-- 【パスワードについて】
-- 全ユーザーで同一の BCrypt ハッシュを共有している。負荷試験は実際に
-- POST /auth/login を叩くため、TestFixtures のダミーハッシュでは通らない。
-- かといって100人分を SQL で生成することはできない（pgcrypto は入れない方針）。
--
-- BCrypt は同じパスワードでもソルトが違えばハッシュが変わるが、検証は
-- 「そのハッシュにそのパスワードが合うか」なので、ハッシュを共有しても
-- 全員が同じパスワードでログインできる。生成1回・投入0秒で済む。
--
--   パスワード: PerfTest123
--   ハッシュ  : 下の password_hash（BCrypt cost 10）
--
-- このハッシュは**負荷試験専用DB（snstimeline_perf）にしか存在しない**。
-- 開発用DBにも本番にも入らないため、リテラル埋め込みを許容する。
-- 再生成の手順は perf/README.md を参照。
--
-- 【機密規約】CLAUDE.md 6章に従い、実在の個人名・実在するメールアドレスは
-- 使わない。メールは example.com ドメイン、表示名は機械生成の連番。

-- 決定論性の担保。random() を固定シードにして、毎回まったく同じデータを作る。
-- これがないと「前回と比べて遅くなった」という比較が成立しない。
SELECT setseed(0.42);

INSERT INTO users (email, username, display_name, bio, password_hash, created_at, updated_at)
SELECT
    'perfuser' || LPAD(n::text, 4, '0') || '@example.com',
    'perfuser' || LPAD(n::text, 4, '0'),
    '負荷試験ユーザー' || LPAD(n::text, 4, '0'),
    CASE WHEN n % 3 = 0
         THEN '負荷試験用のプロフィール文です。ユーザー番号は ' || n || ' です。'
         ELSE NULL
    END,
    '$2a$10$xoK16JBpP/hYdP6xJGILd.dMzuMagW41S9wrtCZghOgH9LtTe/Ngm',
    now() - (INTERVAL '120 days') + (n * INTERVAL '1 hour'),
    now() - (INTERVAL '120 days') + (n * INTERVAL '1 hour')
FROM generate_series(1, 100) AS n;
