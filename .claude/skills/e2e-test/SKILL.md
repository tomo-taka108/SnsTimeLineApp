---
description: Playwrightによる E2Eテスト（シナリオ）とブラウザパフォーマンステストを実行する。専用DBの起動・自動クリーンアップを含む。任意のタイミングで実行するものであり、コミット前の必須チェックではない。
allowed-tools: Bash
disable-model-invocation: false
---

# E2Eテスト／ブラウザパフォーマンステストの実行手順

**これは任意実行の手順である。** コミット前に必須の品質チェック
（[quality-check](../quality-check/SKILL.md)）とは独立していて、
E2Eが緑でないとコミットできない、ということはない。

> **なぜ quality-check に入れないのか**
> quality-check は「Step 1〜6 をすべて緑にしてからコミットする」という
> 必須シーケンス。E2EはDocker + backend + frontend ビルド + previewの4つが
> 揃わないと動かず数分かかる。そこに足すと構造上「毎コミット前に必須」の
> 意味になってしまう。詳細は [docs/14_e2e_test.md](../../../docs/14_e2e_test.md)。

設計と判断の根拠は [docs/14_e2e_test.md](../../../docs/14_e2e_test.md)、
実行手順の詳細は [e2e/README.md](../../../e2e/README.md) を参照。

---

## 前提の確認

```bash
docker compose ps    # Docker Desktop が起動していること
```

初回は Playwright のブラウザバイナリが要る:

```bash
cd e2e && npm install && npx playwright install chromium
```

---

## E2Eシナリオテスト（正しく動くかの検証）

### Step 1: E2E用DBを起動する

```bash
docker compose --profile e2e up -d db-e2e
```

- `--profile e2e` が必要。**付けないと起動しない**（日常の開発で邪魔しないための設計）
- 開発用DB（`snstimeline` / 5432）とは別物（`snstimeline_e2e` / 5434、volume も別）

### Step 2: アプリをE2E用DBに向けて起動する

```bash
export JWT_SECRET=$(openssl rand -base64 48)
cd backend
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173 \
DB_URL=jdbc:postgresql://localhost:5434/snstimeline_e2e ./mvnw spring-boot:run
```

**`CORS_ALLOWED_ORIGINS` を忘れないこと。** `http://localhost:4173` はブラウザ
パフォーマンステスト（vite preview）用。`DB_URL` を付け忘れると開発用DBに
繋がってしまい、試験の前後のTRUNCATEが開発データを消す。

### Step 3: フロントエンドを起動する

バックエンドを既定の8080で起動した場合は不要（`frontend/.env.development` が
既に8080を指す）。**別ポート（例: 8081）で起動した場合は、フロントも同じ
ポートへ向け直す必要がある。**

```bash
cd frontend
VITE_API_BASE_URL=http://localhost:8081/api/v1 npm run dev
```

これを忘れると「ログインに失敗した」という誤解を招くエラーで止まる
（原因はCORSでもバリデーションでもなく、フロントが別のバックエンドを見ていること）。

### Step 4: シナリオテストを実行する

```bash
./e2e/run-e2e.sh
```

| # | 処理 |
|---|---|
| 1 | 事前チェック（DB起動 / テーブル存在 / アプリ疎通 / CORS / フロントエンド疎通） |
| 2 | **前掃除**（前回の残骸があっても正しい状態から始める） |
| 3 | Playwright実行（19テストケース、約20秒） |
| 4 | **後掃除**（TRUNCATE） |

**データの後片付けは自動。** `trap` を張っているため、正常終了・異常終了・
テスト失敗・Ctrl+C・kill のいずれでも掃除が走る。手動での掃除は不要。

### Step 5: 結果を読む

```
ok 1 [e2e] › specs\auth.spec.ts:14:3 › 認証 › 新規登録からログアウトまでの一連の流れ (7.6s)
```

`ok` が合格、`x` が不合格。失敗時はスクリーンショット・トレースが
`e2e/results/test-results/` に残る。HTMLレポートで確認する:

```bash
cd e2e && npm run report
```

---

## ブラウザパフォーマンステスト（LCP/FCP/CLS/TTFBの計測）

**測定対象は本番ビルド（`vite preview`）。** Step 1・2 までは共通。

### Step 3': 本番ビルドして preview を起動する

```bash
cd frontend
VITE_API_BASE_URL=http://localhost:8081/api/v1 npm run build
npm run preview
```

**`npm run build` の方に `VITE_API_BASE_URL` を付けること。** `vite preview`
起動時に渡しても効かない（ビルド時に静的にバンドルへ焼き込まれる値のため）。

### Step 4': 計測を実行する

```bash
./e2e/run-e2e.sh perf
```

`e2e/results/perf-metrics.json`（生データ、5回分）と
`e2e/results/perf-metrics.md`（人間が読む表、中央値と全サンプル）が出力される。

```bash
start e2e/results/perf-metrics.md   # Windows
```

| 指標 | 見方 |
|---|---|
| **LCP** | **主役。** 最大の要素が描画されるまで。閾値は2500ms（Core Web Vitalsの"Good"） |
| CLS | レイアウトのガタつき。閾値は0.1 |
| TTFB | サーバーの初速 |
| FCP | 最初に何かが出るまで |

**絶対値を「速い/遅い」の根拠にしない。** 同一マシン上でブラウザ・アプリ・DBが
CPUを奪い合うため、数値は相対比較（退行検知）に使う。詳細は
[docs/14_e2e_test.md](../../../docs/14_e2e_test.md) 0.2。

---

## 重要事項

- **E2Eはコミット前の必須チェックではない。** 任意のタイミングで実行する
- **開発用DBを汚さない。** Step 2 の `DB_URL` を必ず確認すること
- **フロントエンドの接続先を必ず確認する。** バックエンドを別ポートで起動したら、
  フロントも同じポートへ向け直す（Step 3）
- 無限スクロールのテストは、極端な高負荷（複数ワーカー並列実行）下で
  まれにflakyになることがある（[docs/14_e2e_test.md](../../../docs/14_e2e_test.md) 4.4）。
  `retries: 1` で通常は吸収される

### トラブルシュート

| 症状 | 対処 |
|---|---|
| `db-e2e が起動していない` | Step 1 を実行する。`--profile e2e` を忘れていないか |
| `テーブルが存在しない` | Step 2 を実行してアプリに Flyway を走らせる |
| `アプリに接続できない` | Step 2 のアプリが起動しているか、ポートが空いているか |
| **`ログイン失敗」というエラーでテストが落ちる** | Step 3 を確認。フロントがバックエンドと違うポートを見ていないか |
| `CORSプリフライトが通らない` | Step 2 の `CORS_ALLOWED_ORIGINS` に4173（previewモードの場合）が入っているか |
| **previewのAPI接続先が古い** | `run-e2e.sh perf` が配信中のバンドルを検査して検出する。`VITE_API_BASE_URL` 付きで再ビルドすること |
| 無限スクロールのテストがまれに失敗する | 単発の実行なら実装のバグではなく、環境負荷によるflakiness。再実行で通常は解消する |
