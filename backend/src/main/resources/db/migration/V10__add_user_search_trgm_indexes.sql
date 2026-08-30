-- ユーザー検索・第2段階: 部分一致（docs/04_data_model.md 4章 ⑨, 6.3 / F-US-05 / API #20）
--
-- 第1段階（V9）の前方一致では「田中たろう」を「たろう」で検索しても見つからない。
-- B-treeインデックスは左端が確定している場合しか使えないため、LIKE '%たろう%' は Seq Scan になる
-- （docs/04_data_model.md 6.4）。中間一致を高速化するには別の仕組みが要る。
--
-- pg_trgm は文字列を3文字の断片（トライグラム）に刻んで転置インデックス（GIN）を張る。
--   "たろう"    -> ["  た", " たろ", "たろう", "ろう "]
--   "田中たろう" -> [..., "中たろ", "たろう", "ろう "]   共通する断片があるので候補として拾える
--
-- pg_trgm は PostgreSQL 13以降 trusted extension のため、対象DBへの CREATE 権限があれば
-- スーパーユーザーでなくても実行できる（docs/04_data_model.md 7章）。Flyway実行ユーザーで通る。
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_users_username_trgm
    ON users USING gin (username gin_trgm_ops);

CREATE INDEX idx_users_display_name_trgm
    ON users USING gin (display_name gin_trgm_ops);

COMMENT ON INDEX idx_users_username_trgm     IS 'ユーザー検索・部分一致（F-US-05 第2段階、docs/09_decision_log.md D-49）';
COMMENT ON INDEX idx_users_display_name_trgm IS 'ユーザー検索・部分一致（F-US-05 第2段階、docs/09_decision_log.md D-49）';
