-- follows: フォロー関係（docs/04_data_model.md 2.5）
--
-- 注意: フォロー登録API（#21/#22）とプロフィール画面（SC-05）は今回のスコープ外。
--       この表は #5 タイムライン取得の tab=following のSQLを成立させるために先行して作る。
--       行を書き込むコードはまだ無いため、フォロー中タブには
--       「自分の投稿のみ」が表示される。これは F-TL-02 / D-11
--       （フォロー中TLに自分の投稿を含める）どおりの正しい動作である。
CREATE TABLE follows (
    id           BIGSERIAL     PRIMARY KEY,

    -- follower と followee の取り違えに注意（docs/08_glossary.md）。
    -- 「AさんがBさんをフォローする」= (follower_id = A, followee_id = B) の1行
    follower_id  BIGINT        NOT NULL,
    followee_id  BIGINT        NOT NULL,

    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- 重複フォローを防ぐ
    CONSTRAINT uq_follows_follower_followee UNIQUE (follower_id, followee_id),

    -- 自己フォローをDBレベルで防ぐ（docs/04_data_model.md 3.6）。
    -- アプリ層でも400を返して親切なメッセージを出すが、
    -- 実装漏れがあってもデータ不整合を許さないための二重化である
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id),

    CONSTRAINT fk_follows_follower
        FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_followee
        FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE
);

-- フォロー中タイムライン（F-TL-02）: follower_id から followee 群を引く
CREATE INDEX idx_follows_follower ON follows (follower_id, followee_id);

-- フォロワー一覧（F-FL-04、Phase2）
CREATE INDEX idx_follows_followee ON follows (followee_id, follower_id);

COMMENT ON TABLE  follows             IS 'フォロー関係。1行が「follower が followee をフォローしている」を表す';
COMMENT ON COLUMN follows.follower_id IS 'フォローする側';
COMMENT ON COLUMN follows.followee_id IS 'フォローされる側';
