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

**前提**: JDK 25 / Docker Desktop / Node 20+（フロント着手後）。

> **`docker-compose.yml` と `backend/` は実装済み。** 上記のうち PostgreSQL とバックエンドの手順はそのまま動く。
> **`frontend/` はまだ存在しない**ため、`npm run dev` を含む手順は実行できない。雛形を作った時点で本スキルを更新すること。

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

# 4. フロントエンド（別ターミナル）※ frontend/ 未作成のため現時点では実行不可
cd frontend && npm install && npm run dev
```

> **重要: Spring Boot は `.env` を自動では読み込まない。**
> `.env` は「IDEの実行構成やシェルに設定する値の転記元」として使う。
> `application.yml` の既定値が `.env.example` と一致しているため、
> **実際に設定が必要なのは `JWT_SECRET` だけ**（既定値が無く、未設定だと起動に失敗する）。
>
> ```powershell
> # PowerShell で一時的に設定する例
> $env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
> ```

### バックエンド単体の動作確認（フロント未実装のうちはこちら）

```bash
BASE=http://localhost:8080/api/v1
curl -i -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d '{"email":"taro@example.com","username":"taro_123","displayName":"たろう","password":"Password1"}'

RES=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"taro@example.com","password":"Password1"}')
ACCESS=$(echo "$RES" | jq -r .accessToken)
REFRESH=$(echo "$RES" | jq -r .refreshToken)

curl -i "$BASE/auth/me" -H "Authorization: Bearer $ACCESS"

# トークン再発行（リフレッシュトークンは使い捨てなので、返り値で必ず上書きする）
RES2=$(curl -s -X POST "$BASE/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}")
ACCESS=$(echo "$RES2" | jq -r .accessToken)
REFRESH=$(echo "$RES2" | jq -r .refreshToken)

# ログアウト（204。リフレッシュトークンが失効する）
curl -i -X POST "$BASE/auth/logout" -H "Authorization: Bearer $ACCESS"
```

フロント実装後はブラウザで http://localhost:5173 を開く。

## DBリセット（開発データを初期状態に戻す）

```bash
docker compose down -v && docker compose up -d
# バックエンドを再起動するとマイグレーションが最初から流れる
```

> **現時点で存在するマイグレーションは `V1__create_users.sql` と `V2__create_refresh_tokens.sql` の2本。**
> シードデータ（`V9__insert_seed_data.sql`）は未作成のため、リセット後のDBは空になる。
> 動作確認用のユーザーは上記「バックエンド単体の動作確認」の signup で作る。

シードデータを作る際の方針:

- 内容は [docs/04_data_model.md](../../../docs/04_data_model.md) 8章を参照
- 全ユーザー共通の平文パスワード（例: `Password1`）をBCryptハッシュ化して投入する
- **実在の個人名・実在するメールアドレスを使わない**（`example.com` ドメインを使う）

## よくあるトラブルと対処

### CORSエラー（フロントからAPIを呼ぶと失敗する）

**フロント:5173 とバック:8080 は別オリジン**のため、CORS設定が必須
（[docs/05_api_design.md](../../../docs/05_api_design.md) 7章）。

- `.env` の `CORS_ALLOWED_ORIGINS` に `http://localhost:5173` が入っているか確認する
- **ワイルドカード `*` は使わない**（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 3.7）
- プリフライト（OPTIONS）が401で弾かれていないか、ブラウザのNetworkタブで確認する

### 401 が返り続ける / ログイン状態が維持されない

- `JWT_SECRET` が起動ごとに変わっていないか確認する（変わると既存トークンが全て無効になる）
- **アクセストークンの有効期限は15分**（`JWT_ACCESS_EXPIRATION_MINUTES`）。15分で401になるのは正常な挙動で、`POST /auth/refresh` で再発行する
- リフレッシュトークンの有効期限は14日（`JWT_REFRESH_EXPIRATION_DAYS`）
- **`/auth/refresh` が急に401になる場合、同じリフレッシュトークンを2回使っていないか確認する。** 使い捨て（ローテーション）のため、再利用は盗用とみなされ、そのログインのトークンが全て失効する
- フロントは `localStorage` にJWTを保持する。ブラウザのApplicationタブで確認する

### Flyway のマイグレーションが失敗する

```bash
docker compose logs db --tail=50
```

- **適用済みマイグレーションを編集すると checksum 不一致で失敗する。**
  修正は新しいバージョン（`V10__...`）を追加して行う（[docs/04_data_model.md](../../../docs/04_data_model.md) 7章）
- 開発中は作り直すほうが早い: `docker compose down -v && docker compose up -d`

### アプリが起動せず「JWT_SECRET は32バイト以上である必要があります」で落ちる

意図的な動作。弱い鍵で署名し続けるより明確に落とす設計になっている
（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 3.2）。
`JWT_SECRET` を32バイト以上のランダム文字列に設定して再起動する。

### ビルドが `NoSuchMethodError` で失敗する（JDK 25 特有）

**JDK 25 は javac の内部APIが変わっており、追随していないビルドプラグインが落ちる。**
実際に Spotless 2.44.4 + google-java-format で発生し、3.10.0 / 1.36.1 へ更新して解決した。
`NoSuchMethodError` を見たら、まずプラグインのバージョンを疑うこと。

同様に、**`start.spring.io` のメタデータが最新のライブラリ対応状況に追いついていない**ことがある
（MyBatis が Boot 4.1 非対応と表示された）。Maven Central の POM で実際の対応バージョンを確認する。

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
