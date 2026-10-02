# E2Eテスト（Playwright）

設計と判断の根拠は [docs/14_e2e_test.md](../docs/14_e2e_test.md) を参照。
ここには**実行手順だけ**を書く。

## 前提

| 必要なもの | 確認コマンド | 備考 |
|---|---|---|
| Docker Desktop | `docker compose ps` | E2E用DBの起動に使う |
| Playwright のブラウザバイナリ | `npx playwright install chromium` | 初回だけ必要 |

```bash
cd e2e && npm install && npx playwright install chromium
```

## E2Eシナリオテスト

```bash
# 1. E2E用DBを起動する（profiles 付きなので通常の up では起動しない）
docker compose --profile e2e up -d db-e2e

# 2. アプリをE2E用DBに向けて起動する（別ターミナル）
export JWT_SECRET=$(openssl rand -base64 48)
cd backend
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173 \
DB_URL=jdbc:postgresql://localhost:5434/snstimeline_e2e ./mvnw spring-boot:run

# 3. フロントエンドを起動する（別ターミナル。バックエンドが既定8080なら不要）
cd frontend
VITE_API_BASE_URL=http://localhost:8081/api/v1 npm run dev

# 4. テストを実行する（前掃除 → Playwright → 後片付けまで自動）
./e2e/run-e2e.sh
```

**開発用DB（`snstimeline` / 5432）は一切触らない。** 専用DB（`snstimeline_e2e` / 5434）で完結する。

### 環境変数

| 変数 | 既定 | 用途 |
|---|---|---|
| `E2E_API_BASE_URL` | `http://localhost:8080/api/v1` | バックエンドの接続先 |
| `E2E_BASE_URL` | `http://localhost:5173` | フロントエンド（devサーバー）の接続先 |
| `E2E_PERF_BASE_URL` | `http://localhost:4173` | フロントエンド（previewサーバー）の接続先 |

## ブラウザパフォーマンステスト

測定対象は本番ビルド（`vite preview`）。

```bash
# 1〜2 は上記と同じ

# 3'. 本番ビルドしてpreviewを起動する（VITE_API_BASE_URLはビルドコマンドに付ける）
cd frontend
VITE_API_BASE_URL=http://localhost:8081/api/v1 npm run build
npm run preview

# 4'. 計測を実行する
./e2e/run-e2e.sh perf
```

> **`VITE_API_BASE_URL` は `npm run build` に付けること。** `vite preview` の
> 起動時に渡しても効かない。ビルド時に静的にバンドルへ焼き込まれる値のため。

## データの後片付けについて

**試験の前後で自動的に TRUNCATE される。** 手動での掃除は不要。

`run-e2e.sh` は `trap cleanup EXIT INT TERM` を張っているため、
**正常終了・異常終了・テスト失敗・Ctrl+C・kill のいずれでも掃除が走る。**

> Playwright の `globalSetup` は前掃除だけを担う。`globalTeardown` 単独に頼らないのは、
> `globalSetup` が例外を投げると呼ばれないため（k6 の `teardown()` と同じ落とし穴）。

## 型検査

```bash
cd e2e && npm run typecheck
```

`e2e/` は `frontend/` から独立した `package.json` / `tsconfig.json` を持つ。
`@playwright/test` を frontend のビルドに混ぜないため。

## テストデータ

シードSQLは使わない。各テストが `fixtures/api.ts` 経由でAPIを叩き、必要な分だけ
（数件〜25件）のユーザー・投稿を作る。メールは `example.com` ドメイン、
パスワードは `E2ETest123!`。

## 結果の読み方

### E2Eシナリオテスト

```
ok 1 [e2e] › specs\auth.spec.ts:14:3 › 認証 › 新規登録からログアウトまでの一連の流れ (7.6s)
```

HTMLレポートで詳細（スクリーンショット・トレース）を見る:

```bash
cd e2e && npm run report
```

### ブラウザパフォーマンステスト

```
[perf] /login (SC-01): LCP=68.0ms CLS=0.000 TTFB=1.8ms FCP=68.0ms
```

| 見る項目 | 意味 |
|---|---|
| **LCP** | **主役。** 最大の要素が描画されるまで。閾値2500ms（Core Web Vitalsの"Good"） |
| CLS | レイアウトのガタつき。閾値0.1 |
| TTFB | サーバーの初速 |

> **絶対値を「速い/遅い」の根拠にしないこと。** ブラウザ・アプリ・DBが同一マシンで
> CPUを奪い合うため、数値は「このマシンでこの構成のとき」の値でしかない。
> **前回との相対比較（退行検知）に使う。**

結果は `e2e/results/perf-metrics.md`（人間が読む表）と
`e2e/results/perf-metrics.json`（生データ）に出力される。どちらも `.gitignore` 済みで、
**実行のたびに上書きされる**。長期的な記録は [docs/14_e2e_test.md](../docs/14_e2e_test.md)
5.5「ベースライン」に書き残す。
