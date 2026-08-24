-- posts: 投稿（docs/04_data_model.md 2.2）
--
-- 注意: 添付画像（post_images / stored_files）は今回のスコープ外。
--       画像機能（F-IM-01〜03）の実装時に追加する。
--       それまで PostSummary.images は常に空配列を返す。
CREATE TABLE posts (
    id             BIGSERIAL     PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    body           VARCHAR(280)  NOT NULL,

    -- 非正規化カウンタ（docs/09_decision_log.md D-01）。
    -- タイムライン20件ぶんのカウントを毎回 COUNT(*) すると劣化するため、
    -- 集計結果をカラムに持つ。代わりに整合性を保つ責任が発生する:
    --   1. いいね/コメントの登録・削除と同一トランザクションで更新する
    --   2. 更新は必ずSQL側の相対更新（= like_count + 1）で行う。
    --      Javaで「読む→足す→書く」をすると同時実行で更新が失われる
    like_count     INTEGER       NOT NULL DEFAULT 0,
    comment_count  INTEGER       NOT NULL DEFAULT 0,

    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- updated_at と edited_at を分ける理由（docs/04_data_model.md 2.2）:
    -- updated_at はカウンタ更新でも変わってしまうため、
    -- 「ユーザーが本文を編集した」ことの判定には使えない。
    -- UIの「編集済み」表示は専用の edited_at を見る。
    edited_at      TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ,

    -- ON DELETE RESTRICT: 投稿を持つユーザーの物理削除を拒否する。
    -- 退会は users.deleted_at による論理削除で行う方針のため、
    -- 物理削除が走るのは想定外の操作である
    CONSTRAINT fk_posts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- 空白のみの投稿を防ぐ。フロント・DTO・DBの3層で同じ検証を行う
    -- （docs/05_api_design.md 8章）。btrim してから数えるのが要点
    CONSTRAINT ck_posts_body_length    CHECK (char_length(btrim(body)) >= 1),
    CONSTRAINT ck_posts_like_count     CHECK (like_count >= 0),
    CONSTRAINT ck_posts_comment_count  CHECK (comment_count >= 0)
);

-- 全体タイムライン（F-TL-01）。
-- 部分インデックスなので、クエリ側にも同じ WHERE deleted_at IS NULL が
-- 必要（無いとインデックスが使われない）。docs/04_data_model.md 4章
CREATE INDEX idx_posts_timeline
    ON posts (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

-- フォロー中タイムライン（F-TL-02）の起点、
-- およびプロフィールの投稿一覧（F-US-02）
CREATE INDEX idx_posts_user_created
    ON posts (user_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  posts               IS '投稿。論理削除（deleted_at）を採用する';
COMMENT ON COLUMN posts.body          IS '本文。最大280文字、空白のみ不可（MD-01の仕様と一致）';
COMMENT ON COLUMN posts.like_count    IS '非正規化カウンタ。同一トランザクション内でSQL相対更新する（D-01）';
COMMENT ON COLUMN posts.comment_count IS '同上。コメント機能は未実装のため現状は常に0';
COMMENT ON COLUMN posts.updated_at    IS 'レコードの更新日時。カウンタ更新でも変わる';
COMMENT ON COLUMN posts.edited_at     IS '本文が編集された日時。値があればUIに「編集済み」を表示する';
COMMENT ON COLUMN posts.deleted_at    IS '論理削除。削除済み投稿はタイムラインにも詳細にも出さない';


-- likes: いいね（docs/04_data_model.md 2.4）
--
-- 注意: いいねAPI（#14/#15）は今回のスコープ外で、この表に書き込むコードはまだ無い。
--       投稿カードのいいねボタンは表示のみ（常に0件）。
--       テーブルを先に作るのは、posts.like_count と対で意味を成す設計であり、
--       あとから別マイグレーションに切り出す必然性が無いため。
--
-- deleted_at を持たない＝物理削除（docs/09_decision_log.md D-02）。
-- 論理削除にすると UNIQUE (post_id, user_id) が
-- 「いいね→解除→再いいね」で衝突してしまう。履歴を残す要件も無い。
CREATE TABLE likes (
    id          BIGSERIAL     PRIMARY KEY,
    post_id     BIGINT        NOT NULL,
    user_id     BIGINT        NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- 二重いいねを防ぐ最後の砦。
    -- アプリ側の事前チェックだけでは同時実行（TOCTOU）を防げない
    CONSTRAINT uq_likes_post_user UNIQUE (post_id, user_id),

    CONSTRAINT fk_likes_post
        FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_likes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- isLikedByMe の一括判定（docs/04_data_model.md 5.3）。
-- uq_likes_post_user は (post_id, user_id) 起点なので、
-- user_id を先頭にしたこのインデックスが別途必要
CREATE INDEX idx_likes_user_post ON likes (user_id, post_id);

COMMENT ON TABLE  likes            IS 'いいね。論理削除を持たない（物理削除、D-02）';
COMMENT ON COLUMN likes.created_at IS 'SC-10「いいねしたユーザー一覧」（Phase2）の並び順に使う';
