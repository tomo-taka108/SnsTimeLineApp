#!/usr/bin/env bash
#
# E2Eテストの実行ラッパー（docs/14_e2e_test.md）。
#
# perf/run-perf.sh（D-66）と同じ trap 方式。Playwright の globalSetup は
# 前掃除だけを担い、後掃除はこのスクリプトの trap が担う
# （globalSetup が例外を投げると globalTeardown は呼ばれない。
#  k6 の teardown() と同じ落とし穴のため、外側の trap に一本化する）。
#
# 使い方:
#   1. docker compose --profile e2e up -d db-e2e
#   2. CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173 \
#      DB_URL=jdbc:postgresql://localhost:5434/snstimeline_e2e ./mvnw spring-boot:run
#   3. VITE_API_BASE_URL=http://localhost:8081/api/v1 npm run dev
#      （バックエンドを別ポート(例:8081)で起動した場合は、フロントも同じポートへ
#        向ける必要がある。frontend/.env.development は :8080 固定のため、
#        既定ポート(8080)でバックエンドを起動するなら3は不要）
#   4. ./e2e/run-e2e.sh              （通常のE2Eシナリオ。3のdevサーバーに対して実行）
#
# ブラウザパフォーマンステスト（本番ビルドに対して実行）の場合は 3 の代わりに:
#   3'. cd frontend && VITE_API_BASE_URL=http://localhost:8081/api/v1 npm run build \
#       && npm run preview
#       （VITE_API_BASE_URL はビルド時に静的にバンドルへ焼き込まれる値で、
#         vite preview 起動時に渡しても効かない。ビルドコマンドに付けること）
#   4'. ./e2e/run-e2e.sh perf
#
# Git Bash（Windows）での実行を想定している。

set -euo pipefail

# Git Bash のパス変換を無効化する必要があるのは、コンテナ内の絶対パスを
# docker compose exec に渡す psql_file() / psql_cmd() の呼び出し内だけ。
# MSYS_NO_PATHCONV と MSYS2_ARG_CONV_EXCL="*" のどちらも、プロセス全体に
# export すると curl の -w '%{http_code}' のようなフォーマット文字列の
# 処理まで巻き込んで壊すことを実機で確認した（例: 200 が "200000" になる）。
# perf/run-perf.sh は MSYS_NO_PATHCONV をグローバルに export しており、
# 本来は同じ不具合を抱えているが、app_status の判定が !=  "000" という
# 緩い比較のため症状が表面化していない。ここでは関数呼び出し時だけに限定する

# ---- 設定（環境変数で上書き可能）-------------------------------------------

BASE_URL="${E2E_API_BASE_URL:-http://localhost:8080/api/v1}"
FRONTEND_URL="${E2E_BASE_URL:-http://localhost:5173}"
PREVIEW_URL="${E2E_PERF_BASE_URL:-http://localhost:4173}"

# 引数 "perf" を渡すとブラウザパフォーマンステストのみ実行する
MODE="${1:-e2e}"

readonly COMPOSE_SERVICE="db-e2e"
readonly DB_USER="snsapp"
readonly DB_NAME="snstimeline_e2e"

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# ---- 共通処理 ---------------------------------------------------------------

log() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m[warn] %s\033[0m\n' "$*" >&2; }

psql_file() {
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" docker compose exec -T "$COMPOSE_SERVICE" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q -f "$1"
}

psql_cmd() {
  MSYS_NO_PATHCONV=1 docker compose exec -T "$COMPOSE_SERVICE" \
    psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -tAc "$1"
}

# ---- 後片付け ---------------------------------------------------------------

_cleaned=0

cleanup() {
  local exit_code=$?

  if [ "$_cleaned" = "1" ]; then return $exit_code; fi
  _cleaned=1

  if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$COMPOSE_SERVICE"; then
    warn "${COMPOSE_SERVICE} が起動していないため、後片付けをスキップした"
    exit $exit_code
  fi

  log "データを掃除する（TRUNCATE）"
  if psql_file /e2e-sql/truncate-all.sql; then
    echo "掃除が完了した"
  else
    warn "掃除に失敗した。手動で確認すること:"
    warn "  docker compose exec db-e2e psql -U ${DB_USER} -d ${DB_NAME}"
  fi

  # EXIT トラップ内で return すると終了ステータスが上書きされる（perf/run-perf.sh と同じ罠）。
  # 明示的に exit し直すことで、Playwright の非0終了（テスト失敗）を呼び出し元に伝える。
  exit $exit_code
}

trap cleanup EXIT INT TERM

# ---- 事前チェック -----------------------------------------------------------

log "事前チェック"

if ! docker compose ps --status running --services 2>/dev/null | grep -qx "$COMPOSE_SERVICE"; then
  echo "エラー: ${COMPOSE_SERVICE} が起動していない。先に次を実行すること:" >&2
  echo "  docker compose --profile e2e up -d ${COMPOSE_SERVICE}" >&2
  exit 1
fi

for _ in $(seq 1 30); do
  if psql_cmd 'SELECT 1' >/dev/null 2>&1; then break; fi
  sleep 1
done
psql_cmd 'SELECT 1' >/dev/null

if [ "$(psql_cmd "SELECT to_regclass('public.users') IS NOT NULL")" != "t" ]; then
  echo "エラー: テーブルが存在しない。先にアプリをE2E用DBに向けて起動すること:" >&2
  echo "  CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173 \\" >&2
  echo "  DB_URL=jdbc:postgresql://localhost:5434/${DB_NAME} ./mvnw spring-boot:run" >&2
  exit 1
fi

# curl の行をバックスラッシュで複数行に分けると、Git Bash 環境で
# -w '%{http_code}' の出力が稀に重複する不具合を実機で確認した（例: "200" が
# "200000" になる）。1行にまとめることで再現しなくなったため、この形式にする
app_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X POST "${BASE_URL}/auth/login" -H 'Content-Type: application/json' -d '{}' 2>/dev/null || echo '000')"

if [ "$app_status" = "000" ]; then
  echo "エラー: アプリに接続できない（${BASE_URL}）" >&2
  echo "  次を実行してアプリを起動すること:" >&2
  echo "  CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173 \\" >&2
  echo "  DB_URL=jdbc:postgresql://localhost:5434/${DB_NAME} ./mvnw spring-boot:run" >&2
  exit 1
fi

echo "OK（接続先: ${BASE_URL}）"

# ---- CORS 事前チェック（E2E特有。perfには無い問題）--------------------------
#
# vite preview の既定ポートは4173で、CORS_ALLOWED_ORIGINS に含まれていないと
# 全APIがCORSエラーで失敗する。原因が分かりにくい罠なので先回りして検出する
# （dev-environment スキルが警告している「CORSエラーの海」を未然に防ぐ）。

if [ "$MODE" = "perf" ]; then
  log "CORS事前チェック（${PREVIEW_URL} からのプリフライト）"
  # 1行にまとめる理由は app_status と同じ（上記コメント参照）。
  # ここは厳密な値一致（"200"/"204"）で判定するため、重複が起きると確実に落ちる
  preflight_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -X OPTIONS "${BASE_URL}/auth/login" -H "Origin: ${PREVIEW_URL}" -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: Content-Type' 2>/dev/null || echo '000')"

  if [ "$preflight_status" != "200" ] && [ "$preflight_status" != "204" ]; then
    echo "エラー: ${PREVIEW_URL} からのCORSプリフライトが通らない（status=${preflight_status}）" >&2
    echo "  アプリ起動時に CORS_ALLOWED_ORIGINS へ ${PREVIEW_URL} を含めること:" >&2
    echo "  CORS_ALLOWED_ORIGINS=http://localhost:5173,${PREVIEW_URL} \\" >&2
    echo "  DB_URL=jdbc:postgresql://localhost:5434/${DB_NAME} ./mvnw spring-boot:run" >&2
    exit 1
  fi
  echo "OK（${PREVIEW_URL} からのCORSが許可されている）"
fi

# ---- フロントエンド事前チェック ----------------------------------------------
#
# frontend/.env.development は VITE_API_BASE_URL を :8080 固定で持つ。
# バックエンドを既定(8080)以外のポートで起動した場合、フロントエンドの
# npm run dev も VITE_API_BASE_URL=... で同じポートに向けて起動し直す必要がある。
# これを忘れると、テストは「ログインに失敗した」という誤解を招くエラーで
# 止まり、原因（ポート不一致）の特定に時間がかかる（実機で経験済み）。
# フロントエンドが生きているかだけは、ここで確認しておく。
front_status="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "${FRONTEND_URL}/login" 2>/dev/null || echo '000')"
if [ "$front_status" = "000" ]; then
  echo "エラー: フロントエンドに接続できない（${FRONTEND_URL}）" >&2
  echo "  cd frontend && npm run dev を実行すること" >&2
  exit 1
fi
echo "OK（フロントエンド: ${FRONTEND_URL}）"
warn "バックエンドを既定(8080)以外のポートで起動した場合は、フロントエンドも"
warn "  VITE_API_BASE_URL=${BASE_URL} npm run dev"
warn "として同じポートへ向け直すこと（frontend/.env.development は8080固定のため）"

# ---- 前掃除 -----------------------------------------------------------------

log "前回のデータを掃除する"
psql_file /e2e-sql/truncate-all.sql
echo "掃除が完了した"

# ---- テスト実行 --------------------------------------------------------------

mkdir -p e2e/results

export E2E_API_BASE_URL="$BASE_URL"
export E2E_BASE_URL="$FRONTEND_URL"
export E2E_PERF_BASE_URL="$PREVIEW_URL"

rc=0
if [ "$MODE" = "perf" ]; then
  log "ブラウザパフォーマンステストを実行する（対象: ${PREVIEW_URL}）"
  if ! curl -s -o /dev/null --max-time 5 "$PREVIEW_URL"; then
    echo "エラー: ${PREVIEW_URL} に接続できない。先に本番ビルドをpreviewで起動すること:" >&2
    echo "  cd frontend && VITE_API_BASE_URL=${BASE_URL} npm run build && npm run preview" >&2
    exit 1
  fi
  # VITE_API_BASE_URL はビルド時に静的にバンドルへ焼き込まれる値で、
  # vite preview はビルド済みの静的ファイルをそのまま配信するだけなので、
  # 起動時に環境変数を渡しても効かない（実機で踏んだ罠）。
  # バンドルに実際に埋め込まれているURLを検査し、E2E用バックエンドを
  # 指していなければ、ビルドし直すよう案内して止める
  bundle_js="$(curl -s "$PREVIEW_URL" | grep -oE '/assets/index-[A-Za-z0-9_-]+\.js' | head -1)"
  if [ -n "$bundle_js" ]; then
    api_base_in_bundle="$(curl -s "${PREVIEW_URL}${bundle_js}" | grep -oE 'localhost:[0-9]+/api/v1' | head -1)"
    expected_host_port="$(echo "$BASE_URL" | sed -E 's#^https?://##; s#/api/v1$##')"
    if [ "$api_base_in_bundle" != "${expected_host_port}/api/v1" ]; then
      echo "エラー: previewのビルドが古いAPI接続先を含んでいる（検出値: ${api_base_in_bundle:-不明}）" >&2
      echo "  VITE_API_BASE_URL を指定してビルドし直すこと:" >&2
      echo "  cd frontend && VITE_API_BASE_URL=${BASE_URL} npm run build && npm run preview" >&2
      exit 1
    fi
  fi
  (cd e2e && npx playwright test --project=perf) || rc=$?
else
  log "E2Eシナリオテストを実行する（対象: ${FRONTEND_URL}）"
  (cd e2e && npx playwright test --project=e2e) || rc=$?
fi

# ---- 結果 -------------------------------------------------------------------

if [ "$rc" -eq 0 ]; then
  log "結果: 合格"
else
  log "結果: 不合格（exit=${rc}）"
fi

echo "レポート: e2e/results/playwright-report/index.html"

exit $rc
