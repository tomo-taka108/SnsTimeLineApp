-- comments: コメント（docs/04_data_model.md 2.3）
--
-- parent_comment_id は持たない＝返信のないフラット構造（docs/09_decision_log.md D-03）。
CREATE TABLE comments (
    id          BIGSERIAL     PRIMARY KEY,
    post_id     BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    body        VARCHAR(280)  NOT NULL,

    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- コメント編集（F-CM-03）はPhase2のため今回は書き込まない。
    -- posts.edited_at と同じ理由でカラムだけ先に用意しておく
    edited_at   TIMESTAMPTZ,
    deleted_at  TIMESTAMPTZ,

    -- ON DELETE RESTRICT: posts / users と同じ方針。
    -- 投稿削除時もコメントは追い削除しない（04_data_model.md 設計判断②）ため、
    -- post_id の親が消える経路自体が無い
    CONSTRAINT fk_comments_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_comments_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT ck_comments_body_length CHECK (char_length(btrim(body)) >= 1)
);

-- 投稿詳細のコメント一覧（F-CM-02）。
-- タイムラインと異なり古い順（昇順）で表示するため ASC で作る。
-- 部分インデックスなので、クエリ側にも同じ WHERE deleted_at IS NULL が必要
CREATE INDEX idx_comments_post_created
    ON comments (post_id, created_at ASC, id ASC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  comments            IS 'コメント。論理削除（deleted_at）を採用する。parent_comment_id は持たない（D-03、フラット構造）';
COMMENT ON COLUMN comments.body       IS '本文。最大280文字、空白のみ不可';
COMMENT ON COLUMN comments.updated_at IS 'レコードの更新日時。カウンタ更新の対象ではないが posts と同じ列構成に揃える';
COMMENT ON COLUMN comments.edited_at  IS '編集機能（F-CM-03）はPhase2。カラムのみ先に用意し、今回は書き込まない';
COMMENT ON COLUMN comments.deleted_at IS '論理削除。削除時に posts.comment_count を -1 する';
