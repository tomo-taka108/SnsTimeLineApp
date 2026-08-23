-- users: ユーザー（docs/04_data_model.md 2.1）
--
-- 注意: avatar_file_id の FK 制約はここでは付けない。
--       users.avatar_file_id -> stored_files と stored_files.uploaded_by -> users が
--       相互参照になるため、V2 で stored_files を作った後に ALTER TABLE で追加する
--       （docs/04_data_model.md 7章）。
CREATE TABLE users (
    id              BIGSERIAL     PRIMARY KEY,
    email           VARCHAR(255)  NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    username        VARCHAR(30)   NOT NULL,
    display_name    VARCHAR(50)   NOT NULL,
    bio             VARCHAR(160),
    avatar_file_id  BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at      TIMESTAMPTZ,

    -- 一意制約に deleted_at を絡めない。
    -- 退会したユーザーのメールアドレス・ユーザー名は再利用させない方針のため
    -- （部分ユニークインデックスにすると退会後に再登録できてしまう）。
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),

    -- 制約名は明示的に付ける。アプリ側で制約名を見て 409 のエラーコードを
    -- 出し分けるため、PostgreSQL の自動命名に任せない。
    CONSTRAINT ck_users_username_length  CHECK (char_length(username) >= 3),
    CONSTRAINT ck_users_username_format  CHECK (username ~ '^[a-zA-Z0-9_]+$'),
    CONSTRAINT ck_users_display_name_len CHECK (char_length(display_name) >= 1)
);

COMMENT ON TABLE  users                IS 'ユーザー。論理削除（deleted_at）を採用する';
COMMENT ON COLUMN users.email          IS 'ログインID。APIレスポンスには含めない（アカウント列挙防止）';
COMMENT ON COLUMN users.password_hash  IS 'BCrypt ハッシュ。平文は保存しない';
COMMENT ON COLUMN users.username       IS '@taro_123 のハンドル部分';
COMMENT ON COLUMN users.avatar_file_id IS 'stored_files.id を参照。FK 制約は V2 以降で追加する';
COMMENT ON COLUMN users.deleted_at     IS '論理削除（退会）。MVPでは退会機能なしだがカラムは用意する';
