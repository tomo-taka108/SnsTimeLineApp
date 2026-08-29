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

**前提**: JDK 25 / Docker Desktop / Node 20+。

> **`docker-compose.yml` / `backend/` / `frontend/` はすべて実装済み。** 上記の手順はそのまま動く。
> 現時点で動くのは、認証（新規登録／ログイン／ログアウト）、投稿の作成・表示・編集・削除、
> タイムライン（全体／フォロー中、無限スクロール）、コメント（投稿・表示・削除）、いいね、
> プロフィール表示・編集、フォロー・フォロー中一覧・フォロワー一覧。
> 画像添付・プロフィール画像・ユーザー検索は未実装（[docs/07_architecture.md](../../../docs/07_architecture.md) 7.1）。

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

## 初回セットアップ（最初の1回だけ）

```bash
# 1. 環境変数ファイルを作成（.env はコミットされていない）
cp .env.example .env

# 2. DB起動
docker compose up -d

# 3. フロントエンドの依存をインストール
cd frontend && npm install
```

> **`cp .env.example .env` は最初の1回だけでよい。**
> `.env` は `.gitignore` で除外されているだけで、**ローカルには残り続ける**。
> `git pull` しても消えたり上書きされたりしないため、毎回コピーし直す必要はない
> （既存の `.env` を上書きしないよう、むしろ2回目以降は実行しないこと）。

## 毎回のセッション開始時にやること

**`JWT_SECRET` はターミナルを開き直すたびに設定が必要。**

```powershell
# PowerShell
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
cd backend
./mvnw spring-boot:run
```

```bash
# bash / Git Bash
export JWT_SECRET=$(openssl rand -base64 48)
cd backend && ./mvnw spring-boot:run
```

**なぜ毎回必要なのか。**

| | 実体 | 寿命 |
|---|---|---|
| `.env` ファイル | ディスク上のファイル | 消すまで残る |
| `$env:JWT_SECRET` | **そのターミナル（プロセス）のメモリ上だけの一時変数** | **ターミナルを閉じると消える** |

**重要: Spring Boot は `.env` を自動では読み込まない。** `.env` は「IDEの実行構成やシェルに
設定する値の転記元」であり、アプリが実際に読むのは**OSの環境変数**。
つまり `.env` に `JWT_SECRET` を書いただけでは起動できず、
**`spring-boot:run` を実行するのと同じターミナルで**環境変数として設定する必要がある。
`application.yml` の既定値が `.env.example` と一致しているため、
**既定値が無く必ず設定が必要なのは `JWT_SECRET` だけ**（未設定だと起動に失敗する）。

**コマンドが何をしているか**（`JWT_SECRET` はJWTの署名に使う秘密鍵。これが漏れると
他人が「自分は誰それだ」という偽のトークンを作れてしまうため、ランダムな値にする）:

1. `1..48 | ForEach-Object { Get-Random -Maximum 256 }` — 0〜255の乱数を48個作る（48バイト分）
2. `[Convert]::ToBase64String(...)` — それを文字列として扱える形式（Base64）に変換する
3. `$env:JWT_SECRET = ...` — このターミナルの環境変数として設定する

**S3を使う場合は、加えて `.env` の `APP_STORAGE_TYPE=S3` と AWS の設定が必要**
（下記「画像の保存先を切り替える」を参照）。上のように `set -a && . ./.env && set +a` で
`.env` をまとめて読み込むと、`JWT_SECRET` 以外の値も一度に渡せる。

## 画像の保存先を切り替える（LOCAL / S3）

`.env` の `APP_STORAGE_TYPE` 1行で切り替わる（[09_decision_log.md](../../../docs/09_decision_log.md) D-40）。

| 値 | 保存先 | AWSアカウント |
|---|---|---|
| `LOCAL`（既定） | `backend/uploads/` | **不要** |
| `S3` | S3バケット | 必要 |

**既定は `LOCAL`。** AWSアカウントが無くても開発できる状態を保つ方針のため
（[10_infrastructure.md](../../../docs/10_infrastructure.md) 5章）、`.env.example` も `LOCAL` にしてある。

S3を使う場合は `.env` に以下を設定する（`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` は
IAMユーザー `sns-timeline-app-s3-uploader` のもの。**コミットしないこと**）:

```
APP_STORAGE_TYPE=S3
AWS_S3_BUCKET=snstimelineapp-images-dev
AWS_S3_REGION=ap-northeast-1
AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...
```

> **認証情報はコードに書かない。** AWS SDK が「環境変数 → …… → IAMロール」の順に探すため、
> ローカルでは環境変数、EC2ではIAMロールが自動的に使われる
> （[10_infrastructure.md](../../../docs/10_infrastructure.md) 4.5）。

保存されたか確認する:

```bash
aws s3 ls s3://snstimelineapp-images-dev/ --recursive --profile takashima
```

### バックエンド単体の動作確認（curl）

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

プロフィール・フォローの動作確認（2人目のユーザーを作ってフォローする例）:

```bash
# 2人目を作る
curl -s -X POST "$BASE/auth/signup" -H 'Content-Type: application/json' \
  -d '{"email":"hanako@example.com","username":"hanako","displayName":"はなこ","password":"Password1"}'
RES3=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"hanako@example.com","password":"Password1"}')
ACCESS2=$(echo "$RES3" | jq -r .accessToken)
ME2=$(curl -s "$BASE/auth/me" -H "Authorization: Bearer $ACCESS2" | jq -r .id)

# 1人目（$ACCESS）が2人目（$ME2）をフォロー。2回叩いても followerCount が2にならないことを確認する（冪等性）
curl -s -X PUT "$BASE/users/$ME2/follow" -H "Authorization: Bearer $ACCESS" | jq
curl -s -X PUT "$BASE/users/$ME2/follow" -H "Authorization: Bearer $ACCESS" | jq

# プロフィール取得（isFollowing / postCount / followingCount / followerCount を確認）
curl -s "$BASE/users/$ME2" -H "Authorization: Bearer $ACCESS" | jq

# フォロワー一覧
curl -s "$BASE/users/$ME2/followers" -H "Authorization: Bearer $ACCESS" | jq

# 自分自身のフォロー→400 SELF_FOLLOW_NOT_ALLOWED になることを確認
curl -i -X PUT "$BASE/users/$ME2/follow" -H "Authorization: Bearer $ACCESS2"
```

ブラウザで確認する場合は http://localhost:5173 を開く。

## DBリセット（開発データを初期状態に戻す）

```bash
docker compose down -v && docker compose up -d
# バックエンドを再起動するとマイグレーションが最初から流れる
```

> **現時点のマイグレーションは `V1__create_users.sql` / `V2__create_refresh_tokens.sql` /
> `V3__create_posts_and_likes.sql` / `V4__create_follows.sql` / `V5__create_comments.sql` /
> `V6__create_stored_files.sql` の6本。**
> （`stored_files` は設計書では `V3` 想定だったが、V3〜V5 が先に埋まったため `V6` で作成した）
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

> **5173 が埋まっていると Vite は黙って 5174 にずれる。**
> `Port 5173 is in use, trying another one...` と出た場合、画面は開けるが
> **APIリクエストがすべてCORSで失敗する**（バックエンドは 5173 のみ許可しているため）。
> エラーがCORSの形で出るので原因が分かりにくい。**まず Vite の起動ログでポート番号を確認すること。**
> 多くは「既に別ターミナルで `npm run dev` が動いている」だけなので、二重起動を疑う。

### 画像アップロードが失敗する

- 保存先ディレクトリ（`APP_STORAGE_LOCAL_PATH`、既定 `./uploads`）が存在し書き込み可能か確認する
- サイズ上限は5MB（`APP_UPLOAD_MAX_SIZE_MB`）。**5MB超は 413 `FILE_TOO_LARGE`**。
  Spring側の `spring.servlet.multipart.max-file-size` は20MBの最後の防波堤で、
  5MBの判定は `FileService` が行う（[09_decision_log.md](../../../docs/09_decision_log.md) D-42）
- 許可形式は JPEG / PNG / WebP のみ（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 3.5）。
  **拡張子やContent-Typeではなく先頭バイト（マジックナンバー）で判定する。**
  `.jpg` にリネームしただけのファイルは 415 `UNSUPPORTED_MEDIA_TYPE` になる（仕様どおり）
- `S3` 利用時に `403` や認証エラーが出る場合、`.env` の `AWS_ACCESS_KEY_ID` /
  `AWS_SECRET_ACCESS_KEY` と、IAMポリシーの対象バケット名を確認する

動作確認（`$ACCESS` は上記 curl 手順で取得したもの）:

```bash
BASE=http://localhost:8080/api/v1
# アップロード（201 で fileId が返る）
curl -s -X POST "$BASE/files" -H "Authorization: Bearer $ACCESS" \
  -F "file=@/path/to/image.png;type=image/png"
# 配信（認証不要。<img src> から直接読めることの確認）
curl -s -o /dev/null -D - "$BASE/files/1" | grep -iE "^HTTP|content-type|cache-control"
```

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

## ブラウザでの動作確認（Chrome DevTools MCP）

**確認の順序**: まず**自分の目でブラウザを操作して確認**し、そのうえで**念のための自動確認**として Chrome DevTools MCP を使う。自動確認だけで済ませない（見た目の違和感は人が見ないと気づけないため）。

`.mcp.json` でプロジェクト単位に設定済み。**このリポジトリでのみ有効**で、他プロジェクトでは別途追加が必要（全プロジェクトで使いたい場合は `--scope user` で追加する）。

```json
{
  "mcpServers": {
    "chrome-devtools": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "chrome-devtools-mcp@latest", "--isolated", "--no-usage-statistics", "--no-performance-crux"]
    }
  }
}
```

| オプション | 理由 |
|---|---|
| `--isolated` | 使い捨てプロファイルで起動する。**普段使いのChromeのログイン情報やCookieに触れない** |
| `--no-usage-statistics` | Googleへの使用統計の送信を止める |
| `--no-performance-crux` | パフォーマンス計測時にURLがGoogleのAPIへ送られるのを止める |
| （`--headless` は付けない） | **動作を目で追えるようにするため**、ブラウザの画面を表示する |

### 初回に必要な操作

プロジェクト単位のMCPは**承認が必要**（`.mcp.json` はリポジトリから来る可能性があるため、勝手には有効にならない安全策）。

```bash
claude mcp list          # ⏸ Pending approval と出たら未承認
```

`claude` を起動すると承認を求められるので許可する。承認後は `✔ Connected` になる。

### 使うときの前提

**先にアプリを起動しておくこと。** MCPはブラウザを操作するだけで、サーバーは立ち上げない。

```bash
docker compose up -d
cd backend && ./mvnw spring-boot:run    # :8080
cd frontend && npm run dev              # :5173
```

### できること

| 用途 | 内容 |
|---|---|
| 画面の確認 | スクリーンショット、DOM構造の取得 |
| 操作 | クリック・入力・遷移 |
| **コンソール** | エラーログの読み取り |
| **ネットワーク** | リクエスト／レスポンスの中身の確認（401やCORSの調査に有効） |

> **ログにパスワード・トークンが出ていないことの確認**にも使える（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 5.2）。

### 外すとき

```bash
claude mcp remove chrome-devtools --scope project
```
