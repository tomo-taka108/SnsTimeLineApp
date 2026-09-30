#!/usr/bin/env bash
#
# 負荷試験の実行ラッパー（docs/13_performance_test.md）。
#
# 「シード投入 → k6 実行 → 後片付け」を1本にまとめ、**どんな終わり方をしても
# 必ずデータを掃除する**ことを保証する。
#
# なぜ k6 の teardown() を使わないのか:
#   1. setup() が異常終了すると teardown() は呼ばれない（k6 公式ドキュメント明記）
#   2. 2回目の Ctrl+C は ExternalAbort で即死する（grafana/k6#2804）
#   3. そもそも k6 は DB に接続できない。TRUNCATE を発行する手段がない
# したがって掃除は k6 の外側、シェルの trap で行う。
#
# 使い方:
#   1. docker compose --profile perf up -d db-perf
#   2. DB_URL=jdbc:postgresql://localhost:5433/snstimeline_perf ./mvnw spring-boot:run
#   3. ./perf/run-perf.sh
#
# Git Bash（Windows）での実行を想定している。

set -euo pipefail

# Git Bash は引数中の `/perf-sql/...` を `C:/Program Files/Git/perf-sql/...` に
# 勝手に書き換える（MSYS のパス変換）。コンテナ内の絶対パスを渡せなくなるため無効化する。
# Linux / macOS では無害な未使用変数になる。
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

# ---- 設定（環境変数で上書き可能）-------------------------------------------

# 被試験アプリの接続先。AWS 構築後（D-21）はここを差し替えるだけで再利用できる
BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"

# 実行するシナリオ。既定は Phase 1 のタイムライン
SCENARIO="${SCENARIO:-perf/scenarios/timeline.ts}"

# シードを投入せず k6 だけ回したいとき SKIP_SEED=1
SKIP_SEED="${SKIP_SEED:-0}"

readonly COMPOSE_SERVICE="db-perf"
readonly DB_USER="snsapp"
readonly DB_NAME="snstimeline_perf"

# リポジトリルートに移動する。どこから叩かれても動くようにするため
cd "$(dirname "${BASH_SOURCE[0]}")/.."

# ---- 共通処理 ---------------------------------------------------------------

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn] %s\033[0m\n' "$*" >&2; }

# psql をコンテナ内で実行する。
# -f でコンテナ内のパスを渡すのは、Git Bash の CRLF / パス変換を避けるため
# （`<` のリダイレクトは Windows で壊れやすい）。
psql_file() {
  docker compose exec -T "$COMPOSE_SERVICE" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q -f "$1"
}

psql_cmd() {
  docker compose exec -T "$COMPOSE_SERVICE" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tAc "$1"
}

# ---- 後片付け ---------------------------------------------------------------

# 全テーブルを空にする。
#
# backend/src/test/resources/sql/truncate-all.sql をマウント経由で参照している。
# コピーしないのは、テーブルを追加したときに片方が腐るのを防ぐため。
# あの SQL は「flyway_schema_history は絶対に含めない」という正しさを
# コメントごと保持しているので、参照すればそれを無償で引き継げる。
# INT を受けると trap INT と trap EXIT の両方が発火し、掃除が二重に走る。
# TRUNCATE は冪等なので実害は無いが、ログが汚れるので一度走ったらスキップする。
_cleaned=0

cleanup() {
  local exit_code=$?

  if [ "$_cleaned" = "1" ]; then return $exit_code; fi
  _cleaned=1

  if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$COMPOSE_SERVICE"; then
    warn "${COMPOSE_SERVICE} が起動していないため、後片付けをスキップした"
    return 0
  fi

  log "データを掃除する（TRUNCATE）"
  if psql_file /perf-sql/truncate-all.sql; then
    echo "掃除が完了した"
  else
    warn "掃除に失敗した。手動で確認すること:"
    warn "  docker compose exec db-perf psql -U ${DB_USER} -d ${DB_NAME}"
  fi

  # EXIT トラップの中で return すると、その戻り値がスクリプト全体の
  # 終了ステータスを**上書きしてしまう**。閾値違反（k6 の exit 99）を
  # 呼び出し元に伝えられないと thresholds が無意味になるため、
  # 元の終了コードで明示的に exit し直す。
  # （trap は既に発火済みなので、この exit で再帰することはない）
  exit $exit_code
}

# EXIT / INT / TERM のすべてで掃除を走らせる。これが try/finally 相当。
#   - EXIT : 正常終了 / set -e による異常終了 / k6 の threshold 違反による非0 exit
#   - INT  : Ctrl+C
#   - TERM : kill
trap cleanup EXIT INT TERM

# ---- 事前チェック -----------------------------------------------------------

log "事前チェック"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$COMPOSE_SERVICE"; then
  echo "エラー: ${COMPOSE_SERVICE} が起動していない。先に次を実行すること:" >&2
  echo "  docker compose --profile perf up -d ${COMPOSE_SERVICE}" >&2
  exit 1
fi

# DB が接続を受け付けるまで待つ（healthcheck とは別に、実際に繋げるかを見る）
for _ in $(seq 1 30); do
  if psql_cmd 'SELECT 1' >/dev/null 2>&1; then break; fi
  sleep 1
done
psql_cmd 'SELECT 1' >/dev/null

# Flyway が走っているか（＝アプリが一度でも起動したか）を確認する。
# テーブルが無い状態でシードを流しても失敗するだけなので、ここで止める。
if [ "$(psql_cmd "SELECT to_regclass('public.users') IS NOT NULL")" != "t" ]; then
  echo "エラー: テーブルが存在しない。先にアプリを負荷試験用DBに向けて起動すること:" >&2
  echo "  DB_URL=jdbc:postgresql://localhost:5433/${DB_NAME} ./mvnw spring-boot:run" >&2
  exit 1
fi

# 被試験アプリが起きているか。
# 空ボディを POST すると 400 が返るのが正常なので、HTTP ステータスが
# 返ってくること自体（= 接続できること）だけを見る。
# curl -f は 400 を失敗扱いにしてしまうので使わない。
app_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 \
  -X POST "${BASE_URL}/auth/login" \
  -H 'Content-Type: application/json' -d '{}' 2>/dev/null || echo '000')"

if [ "$app_status" = "000" ]; then
  echo "エラー: アプリに接続できない（${BASE_URL}）" >&2
  echo "  次を実行してアプリを起動すること:" >&2
  echo "  DB_URL=jdbc:postgresql://localhost:5433/${DB_NAME} ./mvnw spring-boot:run" >&2
  exit 1
fi

echo "OK（接続先: ${BASE_URL}）"

# ---- ① 前掃除 ---------------------------------------------------------------

# 前回が強制終了してデータが残っていても、正しい状態から始められるようにする。
# TRUNCATE は空テーブルに対しても成功するので、無条件に呼んで安全。
log "前回のデータを掃除する"
psql_file /perf-sql/truncate-all.sql
echo "掃除が完了した"

# ---- ② シード投入 -----------------------------------------------------------

if [ "$SKIP_SEED" = "1" ]; then
  warn "SKIP_SEED=1 のためシード投入をスキップした"
else
  log "シードデータを投入する（ユーザー100人 / 投稿10,000件）"
  seed_start=$(date +%s)
  psql_file /perf-seed/01_users.sql
  psql_file /perf-seed/02_posts.sql
  psql_file /perf-seed/03_social.sql
  seed_end=$(date +%s)
  echo "投入が完了した（$((seed_end - seed_start))秒）"

  psql_cmd "SELECT 'users=' || (SELECT count(*) FROM users)
                || ' posts=' || (SELECT count(*) FROM posts)
                || ' likes=' || (SELECT count(*) FROM likes)
                || ' comments=' || (SELECT count(*) FROM comments)
                || ' follows=' || (SELECT count(*) FROM follows)"
fi

# ---- ③ k6 実行 --------------------------------------------------------------

# k6 は DB に繋げないため、シナリオが必要とする id をここで取得して環境変数で渡す。
HOTSPOT_POST_ID="$(psql_cmd "SELECT id FROM posts WHERE body LIKE '[HOTSPOT]%' AND deleted_at IS NULL ORDER BY id LIMIT 1")"
export HOTSPOT_POST_ID
export BASE_URL

mkdir -p perf/results

# JSON と HTML の出力は各シナリオの handleSummary()（perf/lib/report.ts）が
# perf/results/ 配下に書く。--summary-export は使わない（二重管理を避けるため）。
log "k6 を実行する（${SCENARIO}）"
rc=0
k6 run "$SCENARIO" || rc=$?

# ---- ④ カウンタ整合の検証（TRUNCATE の前に行う）----------------------------

# 掃除してから検証しても意味がないので、必ずここで実行する。
# docs/04_data_model.md 3.1 Appendix のSQL。0行なら整合している。
log "カウンタの整合性を検証する"
if [ -f perf/seed/verify-counters.sql ]; then
  mismatch="$(psql_file /perf-seed/verify-counters.sql | tr -d '[:space:]')"
  if [ -n "$mismatch" ]; then
    warn "カウンタにズレがある（D-01 のロストアップデートを疑う）:"
    echo "$mismatch"
    rc=1
  else
    echo "整合している（ズレ 0件）"
  fi
fi

# ---- 結果 -------------------------------------------------------------------

if [ "$rc" -eq 0 ]; then
  log "結果: 合格（閾値をすべて満たした）"
else
  log "結果: 不合格（閾値違反またはカウンタ不整合。exit=${rc}）"
fi

echo "サマリ（JSON）: perf/results/summary.json"
echo "レポート（HTML）: perf/results/summary.html"

# k6 の exit code をそのまま返す。
# trap に上書きされて合否が伝わらないと thresholds が無意味になるため、
# cleanup() は受け取った exit code を return している。
exit $rc
