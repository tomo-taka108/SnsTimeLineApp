-- post_images: 投稿の添付画像（docs/04_data_model.md 2.6）
--
-- MVPのAPIバリデーションは1枚に制限するが、DBは4枚まで対応する（設計判断④）。
-- Phase2で imageFileIds の上限を引き上げるだけで済むようにするため。
--
-- 投稿は論理削除（deleted_at）を使うため、投稿を削除しても ON DELETE CASCADE は
-- 発火しない。つまり削除済み投稿の post_images / stored_files はそのまま残る。
-- これは孤児ファイルと同じ扱いで、MVPでは許容する（R-03）。
CREATE TABLE post_images (
    id             BIGSERIAL   PRIMARY KEY,
    post_id        BIGINT      NOT NULL,
    file_id        BIGINT      NOT NULL,

    -- 表示順（0始まり）。MVPは常に0だが、Phase2の複数枚対応をこのカラムだけで
    -- 迎えられるようにしておく
    display_order  SMALLINT    NOT NULL DEFAULT 0,

    -- 物理削除される投稿（想定外の操作）に追随して画像リンクも消える。
    -- stored_files 自体は ON DELETE RESTRICT のまま（ファイル削除ユーザーの
    -- 物理削除を拒否する方針、V6のコメント参照）
    CONSTRAINT fk_post_images_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_images_file
        FOREIGN KEY (file_id) REFERENCES stored_files (id) ON DELETE RESTRICT,

    -- 同じ投稿内で表示順が重複しない
    CONSTRAINT uq_post_images_post_order UNIQUE (post_id, display_order),

    -- 1投稿あたり最大4枚（DBレベル）。MVPのAPIは1枚に制限する
    CONSTRAINT ck_post_images_display_order CHECK (display_order BETWEEN 0 AND 3)
);

-- 投稿ID群に対する画像の一括取得（N+1回避、docs/09_decision_log.md D-45）に使う
CREATE INDEX idx_post_images_post
    ON post_images (post_id, display_order);

COMMENT ON TABLE  post_images                IS '投稿の添付画像。MVPは1枚、DBは4枚まで対応（設計判断④）';
COMMENT ON COLUMN post_images.display_order  IS '表示順（0始まり）。MVPは常に0';
