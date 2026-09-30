# 負荷試験（k6）

設計と判断の根拠は [docs/13_performance_test.md](../docs/13_performance_test.md) を参照。
ここには**実行手順だけ**を書く。

## 前提

| 必要なもの | 確認コマンド | 備考 |
|---|---|---|
| k6 **v0.57 以上** | `k6 version` | v0.56 以下は TypeScript が動かない |
| Docker Desktop | `docker compose ps` | 負荷試験用DBの起動に使う |
| Git Bash | — | `run-perf.sh` は bash 前提（Windows の場合） |

k6 の導入:

```bash
winget install k6 --source winget
```

## 実行手順

```bash
# 1. 負荷試験用DBを起動する（profiles 付きなので通常の up では起動しない）
docker compose --profile perf up -d db-perf

# 2. アプリを負荷試験用DBに向けて起動する（別ターミナル）
export JWT_SECRET=$(openssl rand -base64 48)
cd backend
DB_URL=jdbc:postgresql://localhost:5433/snstimeline_perf ./mvnw spring-boot:run

# 3. 試験を実行する（シード投入 → k6 → 後片付けまで自動）
./perf/run-perf.sh
```

**開発用DB（`snstimeline` / 5432）は一切触らない。** 専用DB（`snstimeline_perf` / 5433）で完結する。

### 環境変数

| 変数 | 既定 | 用途 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080/api/v1` | 接続先。AWS 構築後（D-21）はここを差し替える |
| `SCENARIO` | `perf/scenarios/timeline.ts` | 実行するシナリオ |
| `SKIP_SEED` | `0` | `1` にするとシード投入を飛ばす（k6 だけ回したいとき） |

## データの後片付けについて

**試験の前後で自動的に TRUNCATE される。** 手動での掃除は不要。

`run-perf.sh` は `trap cleanup EXIT INT TERM` を張っているため、
**正常終了・異常終了・閾値違反・Ctrl+C・kill のいずれでも掃除が走る。**

> k6 の `teardown()` は使っていない。`setup()` が異常終了すると呼ばれず
> （k6 公式ドキュメント明記）、そもそも k6 は DB に接続できないため。

## 型検査

k6 は TypeScript を実行できるが**型検査はしない**（型注釈を剥がすだけ）。
そのため検査は自前で行う。

```bash
cd perf && npm install && npm run typecheck
```

`perf/` は `frontend/` から独立した `package.json` / `tsconfig.json` を持つ。
`@types/k6` を frontend のビルドに混ぜないため。

## シードデータ

| ファイル | 内容 |
|---|---|
| `seed/01_users.sql` | ユーザー100人 |
| `seed/02_posts.sql` | 投稿10,000件（`created_at` を3種類に作り分ける） |
| `seed/03_social.sql` | フォロー / いいね / コメント + カウンタ同期 |
| `seed/verify-counters.sql` | 非正規化カウンタの整合検証（0行なら正常） |

件数は [docs/06_non_functional.md](../docs/06_non_functional.md) 1.1 の想定データ量に合わせている。
`setseed(0.42)` で固定シードにしているため、**何度実行しても同じデータ**になる。

### ログイン用の認証情報

シードが作るユーザーは全員このパスワードでログインできる。

```
メール  : perfuser0001@example.com 〜 perfuser0100@example.com
パスワード: PerfTest123
```

**この値は負荷試験専用DBにしか存在しない。** 開発用DBにも本番にも入らない。

BCrypt ハッシュを再生成したい場合は、開発用DBで一度サインアップして
`password_hash` を取り出し、`seed/01_users.sql` のリテラルを差し替える。

## 結果の読み方

```
http_req_duration{name:GET /timeline}: p(95)=13.07ms  ✓ 'p(95)<1000'
http_req_failed......................: 0.00%
```

| 見る項目 | 意味 |
|---|---|
| **`p(95)`** | **主役。** 100回中95回がこの時間以内に返った |
| `http_req_waiting` | サーバーが考えていた時間（TTFB）。`duration` との差が通信時間 |
| `http_req_failed` | エラー率。0% が正常 |
| `✓` / `✗` | 閾値の合否。違反すると k6 は非0で終了する |

> **絶対値を SLA 判定に使わないこと。** 負荷ツールとアプリとDBが同一マシンで
> CPU を奪い合うため、数値は「このマシンでこの構成のとき」の値でしかない。
> **前回との相対比較（退行検知）に使う。**

結果の JSON は `perf/results/summary.json` に出る（`.gitignore` 済み）。
