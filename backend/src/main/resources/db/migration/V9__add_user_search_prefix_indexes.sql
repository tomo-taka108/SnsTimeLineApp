-- ユーザー検索・第1段階: 前方一致（docs/04_data_model.md 4章 ⑧, 6.2 / F-US-05 / API #20）
--
-- 式インデックスにする理由:
--   検索SQLは lower(username) LIKE lower(:q) || '%' と書く。
--   ILIKE ではなく lower() + LIKE にするのは、ILIKE がこの式インデックスを使わないため。
--   プランナはクエリ側の式とインデックス側の式が「文字通り一致」しないと使ってくれない
--   （docs/04_data_model.md 6.4）。
--
-- text_pattern_ops にする理由:
--   デフォルトの演算子クラスはロケール依存の照合順で並ぶため、LIKE 'x%' を範囲検索
--   （x <= v < y）に書き換える最適化が C ロケール以外では効かない。
--   text_pattern_ops はバイト順で並べるので、ロケールに関係なく前方一致に使える。
--
-- 部分インデックス（WHERE deleted_at IS NULL）にする理由:
--   退会済みユーザーは検索結果に出さない（docs/04_data_model.md 6.5 要求6）ため、
--   インデックスに含める意味がない（同 4.1）。
CREATE INDEX idx_users_username_prefix
    ON users (lower(username) text_pattern_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_users_display_name_prefix
    ON users (lower(display_name) text_pattern_ops)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX idx_users_username_prefix     IS 'ユーザー検索・前方一致（F-US-05 第1段階）';
COMMENT ON INDEX idx_users_display_name_prefix IS 'ユーザー検索・前方一致（F-US-05 第1段階）';
