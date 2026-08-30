-- 使われていないユーザー検索用インデックスを削除する（docs/09_decision_log.md D-50）
--
-- V9（前方一致のB-tree）と V10（pg_trgm の GIN）を作ったが、
-- 最終的な検索SQLは中間一致 LIKE '%q%' だけで絞り込む形に落ち着いたため
-- （D-49）、この4本は EXPLAIN 上で一度も選ばれていない。
--
--   絞り込み: LIKE '%q%'     → 左端が不定なのでB-treeは効かない。Seq Scan
--   並び替え: similarity()   → 各行のスコアを計算するだけなのでGINの出番がない
--
-- 使われないインデックスはコストだけ残る。
--   - ユーザー登録・プロフィール編集のたびに4本を更新する（書き込みが遅くなる）
--   - ディスクを消費する（実測で合計約15MB。特にGINは断片を持つので大きい）
--   - 「あるのに効かない」状態がコードを読む人を混乱させる
--
-- 「将来使うかもしれない」で残さない（YAGNI）。
-- 件数が増えて実測で問題になったら、そのとき必要なものを作り直す。
-- 想定される対処は docs/04_data_model.md 6.8 に記載した。
--
-- pg_trgm 拡張そのものは削除しない。ORDER BY の similarity() で使っており、
-- DROP EXTENSION すると検索SQLが実行時エラーになる。
DROP INDEX IF EXISTS idx_users_username_prefix;
DROP INDEX IF EXISTS idx_users_display_name_prefix;
DROP INDEX IF EXISTS idx_users_username_trgm;
DROP INDEX IF EXISTS idx_users_display_name_trgm;
