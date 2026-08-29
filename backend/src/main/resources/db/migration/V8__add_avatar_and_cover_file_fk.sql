-- users.avatar_file_id への FK 制約を追加し、cover_file_id（カバー画像）を新設する
-- （docs/04_data_model.md 2.1、docs/09_decision_log.md D-47）。
--
-- avatar_file_id は V1 で「FK は V2 以降で追加する」とコメントされていたが、
-- stored_files（V6）作成後に追加するのを失念していた。cover_file_id の追加と
-- 合わせてここで解消する。
ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_file
        FOREIGN KEY (avatar_file_id) REFERENCES stored_files (id) ON DELETE SET NULL;

-- cover_file_id: プロフィール背景（カバー画像）。avatar_file_id と同じ構造
ALTER TABLE users
    ADD COLUMN cover_file_id BIGINT;

ALTER TABLE users
    ADD CONSTRAINT fk_users_cover_file
        FOREIGN KEY (cover_file_id) REFERENCES stored_files (id) ON DELETE SET NULL;

COMMENT ON COLUMN users.cover_file_id IS 'プロフィール背景画像。stored_files.id を参照';
