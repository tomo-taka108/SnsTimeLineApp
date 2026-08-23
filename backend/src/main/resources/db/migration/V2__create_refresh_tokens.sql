-- refresh_tokens: リフレッシュトークン（docs/09_decision_log.md D-29）
--
-- アクセストークン（JWT）は短命・ステートレスのままにし、
-- 長命なリフレッシュトークンだけをDBで管理する。
-- こうすることで「JWTは失効できない」という弱点を、
-- リフレッシュトークン側の失効で埋め合わせる。
CREATE TABLE refresh_tokens (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL,

    -- 生のトークンは保存しない。SHA-256 のハッシュ（64桁の16進）だけを持つ。
    -- DBが漏洩しても、そのままではリフレッシュに使えないようにするため。
    -- パスワードと違いランダム256bitで総当たりが成立しないため、
    -- ソルト付きの低速ハッシュ（BCrypt）ではなくSHA-256で足りる。
    token_hash  CHAR(64)      NOT NULL,

    -- 盗用検知の単位。ログイン1回＝1ファミリー。
    -- ローテーションで新しい行を作っても family_id は引き継ぐ。
    family_id   UUID          NOT NULL,

    expires_at  TIMESTAMPTZ   NOT NULL,

    -- ローテーションで使い終わった時刻。NULL なら未使用（有効）。
    used_at     TIMESTAMPTZ,

    -- 失効した時刻。NULL なら失効していない。
    -- ログアウト、および盗用検知によるファミリー一括失効で埋まる。
    revoked_at  TIMESTAMPTZ,

    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    -- ハッシュは一意。トークンから行を1件に特定するため
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- ファミリー一括失効（盗用検知時）で使う
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);

-- ログアウト（ユーザーの全トークン失効）と、期限切れ行の掃除で使う
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

COMMENT ON TABLE  refresh_tokens            IS 'リフレッシュトークン。生の値ではなくSHA-256ハッシュを保存する';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256ハッシュ（16進64桁）。生トークンは保存しない';
COMMENT ON COLUMN refresh_tokens.family_id  IS '盗用検知の単位。ログイン1回で1つ発行し、ローテーション時も引き継ぐ';
COMMENT ON COLUMN refresh_tokens.used_at    IS 'ローテーションで使用済みになった時刻。使用済みトークンの再提示は盗用とみなす';
COMMENT ON COLUMN refresh_tokens.revoked_at IS '失効時刻。ログアウトまたは盗用検知によるファミリー一括失効で埋まる';
