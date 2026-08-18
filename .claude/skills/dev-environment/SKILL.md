---
description: ローカル開発環境（Docker Compose の PostgreSQL + Spring Boot + React/Vite）の起動・停止・DBリセット・トラブルシュート手順。アプリが起動しない/CORSエラー/ポート競合/マイグレーション失敗などの対処を含む。
allowed-tools: Bash
disable-model-invocation: false
---

# 開発環境（Docker Compose + ローカル実行）操作手順

このプロジェクトは **DBのみDocker、アプリはIDEから直接起動**する構成
（[docs/07_architecture.md](../../../docs/07_architecture.md) 6章）。
IDEからの起動とデバッグを優先するため、**アプリ本体はDockerに入れない**。

| 対象 | 起動方法 | ポート |
|---|---|---|
| PostgreSQL | Docker Compose | 5432 |
| バックエンド（Spring Boot） | `./mvnw spring-boot:run` | 8080 |
| フロントエンド（React + Vite） | `npm run dev` | 5173 |

> **実装フェーズ未着手のため、`backend/` `frontend/` `docker-compose.yml` はまだ存在しない。**
> 雛形を作成した時点で、本スキルのコマンドが実際に動くようになる。

## 起動・停止

```bash
# DB起動（バックグラウンド）
docker compose up -d

# 状態確認（healthy になっていること）
docker compose ps

# 停止（DBデータは名前付きボリューム pgdata に残る）
docker compose down

# 停止＋ボリューム削除（DBを完全リセットしたいときのみ。開発データも消える）
docker compose down -v
```

アプリの起動:

```bash
cd backend  && ./mvnw spring-boot:run    # :8080
cd frontend && npm run dev               # :5173
```

## 初回セットアップ

```bash
# 1. 環境変数ファイルを作成（.env はコミットされていない）
cp .env.example .env
#    JWT_SECRET には32バイト以上のランダム文字列を設定する

# 2. DB起動
docker compose up -d

# 3. バックエンド（起動時にFlywayがマイグレーションを自動実行する）
cd backend && ./mvnw spring-boot:run

# 4. フロントエンド（別ターミナル）
cd frontend && npm install && npm run dev
```

ブラウザで http://localhost:5173 を開く。

## DBリセット（開発データを初期状態に戻す）

Flyway でシードデータ（`V9__insert_seed_data.sql`）を含めて作り直す。

```bash
docker compose down -v && docker compose up -d
# バックエンドを再起動するとマイグレーションが最初から流れる
```

- シードデータの内容は [docs/04_data_model.md](../../../docs/04_data_model.md) 8章を参照
- 全ユーザー共通の平文パスワード（例: `Password1`）をBCryptハッシュ化して投入する

## よくあるトラブルと対処

### CORSエラー（フロントからAPIを呼ぶと失敗する）

**フロント:5173 とバック:8080 は別オリジン**のため、CORS設定が必須
（[docs/05_api_design.md](../../../docs/05_api_design.md) 7章）。

- `.env` の `CORS_ALLOWED_ORIGINS` に `http://localhost:5173` が入っているか確認する
- **ワイルドカード `*` は使わない**（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 3.7）
- プリフライト（OPTIONS）が401で弾かれていないか、ブラウザのNetworkタブで確認する

### 401 が返り続ける / ログイン状態が維持されない

- `JWT_SECRET` が起動ごとに変わっていないか確認する（変わると既存トークンが全て無効になる）
- トークンの有効期限は24時間（`JWT_EXPIRATION_HOURS`）
- フロントは `localStorage` にJWTを保持する。ブラウザのApplicationタブで確認する

### Flyway のマイグレーションが失敗する

```bash
docker compose logs db --tail=50
```

- **適用済みマイグレーションを編集すると checksum 不一致で失敗する。**
  修正は新しいバージョン（`V10__...`）を追加して行う（[docs/04_data_model.md](../../../docs/04_data_model.md) 7章）
- 開発中は作り直すほうが早い: `docker compose down -v && docker compose up -d`

### ポート競合（5432 / 8080 / 5173 が使用中）

- 競合する場合は `docker-compose.yml` の `ports` か、各アプリの設定を空きポートに変更する
- 何が使っているか確認: `netstat -ano | findstr :5432`（PowerShell）
- **ホストに別のPostgreSQLが動いていると5432が埋まりやすい**

### 画像アップロードが失敗する

- 保存先ディレクトリ（`APP_STORAGE_LOCAL_PATH`、既定 `./uploads`）が存在し書き込み可能か確認する
- サイズ上限は5MB（`APP_UPLOAD_MAX_SIZE_MB`）。Spring側の
  `spring.servlet.multipart.max-file-size` でも制限されている点に注意
- 許可形式は JPEG / PNG / WebP のみ（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 3.5）

### タイムラインが遅い / N+1 が疑われる

```sql
EXPLAIN ANALYZE
SELECT p.* FROM posts p
WHERE p.deleted_at IS NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 20;
```

- `Seq Scan` ではなく `Index Scan`（`idx_posts_timeline`）になっているか確認する
- **シードデータが少ないとプランナが全件走査を選ぶ。** 10,000件程度投入してから検証する
- タイムライン取得のSQL発行回数は**3回以内**が要求
  （[docs/06_non_functional.md](../../../docs/06_non_functional.md) 1.3）

## DBへの直接接続

```bash
docker compose exec db psql -U snsapp -d snstimeline
```

```sql
\dt                    -- テーブル一覧
\d+ posts              -- テーブル定義の確認
\di                    -- インデックス一覧
```
