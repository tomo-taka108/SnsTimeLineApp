-- stored_files: アップロードされたファイルのメタ情報（docs/04_data_model.md 2.7）
--
-- 設計書では V3 として定義されていたが、V3〜V5 は既に別の用途で使用済みのため
-- V6 として作成する（適用済みマイグレーションは編集できない、docs/04_data_model.md 7章）。
--
-- このテーブルが画像ストレージ抽象化の要（設計判断⑤ / docs/07_architecture.md 3章）。
-- 物理パスや絶対URLを保存せず、storage_type + storage_key の組で保存先を表現する。
-- これにより LOCAL → S3 の移行が「UPDATE stored_files SET storage_type = 'S3'」と
-- 設定変更だけで済む。URLは都度 FileStorageService が組み立てる。
CREATE TABLE stored_files (
    id                 BIGSERIAL     PRIMARY KEY,

    -- 保存先の種別。実装は FileStorageService の各実装が対応する
    storage_type       VARCHAR(20)   NOT NULL DEFAULT 'LOCAL',

    -- 保存先内での相対パス（例: 2026/08/29/uuid.jpg）。
    -- 絶対URLも物理パスも入れない。保存先を移しても値が使い回せるようにするため
    storage_key        VARCHAR(512)  NOT NULL,

    -- アップロード時の元ファイル名。表示・ダウンロード名の参考にのみ使う。
    -- パスの組み立てには決して使わない（パストラバーサル対策、
    -- docs/06_non_functional.md 3.5）
    original_filename  VARCHAR(255),

    content_type       VARCHAR(100)  NOT NULL,
    size_bytes         BIGINT        NOT NULL,

    -- レイアウトシフト防止のため、表示前に縦横比が分かるようにしておく。
    -- 取得できなかった場合に備えて NULL 許容にする
    width              INTEGER,
    height             INTEGER,

    uploaded_by        BIGINT        NOT NULL,

    -- 孤児ファイル（アップロードされたが投稿されなかったファイル）の
    -- 検出に使う（docs/01_requirements.md R-03）。MVPでは検出のみで削除はしない
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- ON DELETE RESTRICT: ファイルを持つユーザーの物理削除を拒否する。
    -- 退会は users.deleted_at による論理削除で行う方針のため、
    -- 物理削除が走るのは想定外の操作である（posts と同じ方針）
    CONSTRAINT fk_stored_files_user
        FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE RESTRICT,

    -- 同じ保存先に同じキーのファイルが二重登録されるのを防ぐ
    CONSTRAINT uq_stored_files_storage UNIQUE (storage_type, storage_key),

    CONSTRAINT ck_stored_files_storage_type CHECK (storage_type IN ('LOCAL', 'S3')),
    CONSTRAINT ck_stored_files_size_bytes   CHECK (size_bytes > 0)
);

-- 孤児ファイルの検出（R-03）と、ユーザー単位の棚卸しに使う
CREATE INDEX idx_stored_files_uploader_created
    ON stored_files (uploaded_by, created_at DESC);

COMMENT ON TABLE  stored_files                   IS 'アップロードされたファイルのメタ情報。投稿画像とプロフィール画像で共用する';
COMMENT ON COLUMN stored_files.storage_type      IS '保存先種別。LOCAL / S3。FileStorageService の実装と対応する';
COMMENT ON COLUMN stored_files.storage_key       IS '保存先内での相対パス。絶対URL・物理パスは保存しない（設計判断⑤）';
COMMENT ON COLUMN stored_files.original_filename IS 'アップロード時のファイル名。パスの組み立てには使わない';
COMMENT ON COLUMN stored_files.width             IS '画像の幅。レイアウトシフト防止用。取得できなければ NULL';
COMMENT ON COLUMN stored_files.height            IS '画像の高さ。同上';
COMMENT ON COLUMN stored_files.created_at        IS '孤児ファイルの検出に使う（R-03）';
