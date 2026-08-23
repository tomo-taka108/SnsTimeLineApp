# API設計

本書は、[03_screen_design.md](03_screen_design.md)（何を取得したいか）と [04_data_model.md](04_data_model.md)（何を持っているか）を突き合わせて設計したREST APIを定義する。

---

## 1. 共通仕様

### 1.1 基本情報

| 項目 | 内容 |
|---|---|
| ベースURL | `http://localhost:8080/api/v1` |
| プロトコル | HTTP（開発環境）。本番相当ならHTTPS必須 |
| Content-Type | `application/json; charset=UTF-8`（画像アップロードのみ `multipart/form-data`） |
| 文字コード | UTF-8 |
| 日時形式 | ISO 8601 / UTC（例: `2026-08-17T12:34:56Z`） |
| ID形式 | 数値（`BIGINT`）。JSONでは数値型で返す |

> **バージョニング**: パスに `/v1` を含める。学習用途では変更しない想定だが、「APIは破壊的変更に備えてバージョンを切る」という慣習を学ぶために最初から入れておく。

> **ID を数値のまま扱うことについて**: `BIGINT` はJavaScriptの安全整数（2^53-1）を超えうるため、実務では文字列で返すことが多い。本アプリは学習用途でIDが小さく収まるため数値のままとする。この判断は [09_decision_log.md](09_decision_log.md) に記録済み。

### 1.2 認証

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9....
```

**トークンは2種類ある**（[09_decision_log.md](09_decision_log.md) D-29）。

| | アクセストークン | リフレッシュトークン |
|---|---|---|
| 形式 | **JWT**（HS256） | **不透明トークン**（ランダム256bitをURLセーフBase64。DBにSHA-256ハッシュで保存） |
| ペイロード | `sub`（ユーザーID）、`iat`、`exp` | なし（DBの行が実体） |
| 有効期限 | **15分** | **14日** |
| 送信先 | すべての保護されたAPI（`Authorization` ヘッダー） | `POST /auth/refresh` のボディのみ |
| 失効 | **できない**（ステートレスの代償） | **できる**（ログアウト・盗用検知） |

**リフレッシュは1回で使い捨て（ローテーション）。** `POST /auth/refresh` は新しいアクセストークンとリフレッシュトークンの両方を返す。クライアントは古いリフレッシュトークンを破棄して、新しい値を保存し直すこと。

> **使用済みのリフレッシュトークンが再提示されると、そのログインに由来するトークンをすべて失効させる。** トークンが漏れて攻撃者と正規ユーザーの両方が使っている可能性が高いため、どちらにも再ログインを強制する（安全側に倒す）。他の端末のログインは巻き込まない。

**認証が必要なエンドポイントでトークンが無効・期限切れの場合は `401 Unauthorized` を返す。** クライアントは401を受けたら **`POST /auth/refresh` で再発行を試み**、それも401なら両方のトークンを破棄してログイン画面へ遷移する（F-CO-02）。

### 1.3 エラーレスポンス統一形式

すべてのエラーは以下の形式で返す（F-CO-01）。

```json
{
  "timestamp": "2026-08-17T12:34:56Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "入力内容に誤りがあります",
  "path": "/api/v1/posts",
  "errors": [
    { "field": "body", "message": "本文は280文字以内で入力してください" }
  ]
}
```

| フィールド | 必須 | 説明 |
|---|---|---|
| `timestamp` | ○ | エラー発生日時 |
| `status` | ○ | HTTPステータスコード |
| `code` | ○ | アプリケーション定義のエラーコード（下表） |
| `message` | ○ | ユーザーに表示可能な日本語メッセージ |
| `path` | ○ | リクエストパス |
| `errors` | — | フィールド単位のエラー。バリデーションエラー時のみ |

#### エラーコード一覧

| `code` | HTTP | 発生条件 | クライアントの挙動 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | 入力値が不正 | フィールド直下にエラー表示 |
| `SELF_FOLLOW_NOT_ALLOWED` | 400 | 自分自身をフォローしようとした | トースト表示 |
| `INVALID_CREDENTIALS` | 401 | メールまたはパスワードが違う | フォーム上部に表示。**どちらが違うかは示さない** |
| `UNAUTHENTICATED` | 401 | トークンなし / 無効 / 期限切れ | トークン破棄 → SC-01 へ遷移 |
| `FORBIDDEN` | 403 | 他人のリソースを編集・削除しようとした | トースト表示 |
| `NOT_FOUND` | 404 | リソースが存在しない、または論理削除済み | SC-12 へ遷移 |
| `EMAIL_ALREADY_EXISTS` | 409 | メールアドレスが登録済み | 該当フィールドにエラー表示 |
| `USERNAME_ALREADY_EXISTS` | 409 | ユーザー名が使用済み | 該当フィールドにエラー表示 |
| `FILE_TOO_LARGE` | 413 | ファイルサイズ超過 | トースト表示 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 非対応のファイル形式 | トースト表示 |
| `INTERNAL_ERROR` | 500 | サーバー内部エラー | トースト表示 |

#### HTTPステータスの使い分け

| コード | 使う場面 |
|---|---|
| `200 OK` | 取得・更新の成功。**いいね/フォローの冪等な成功も含む** |
| `201 Created` | 新規作成の成功（登録・投稿・コメント・ファイルアップロード） |
| `204 No Content` | 削除の成功（レスポンスボディなし） |
| `400 Bad Request` | バリデーションエラー、業務ルール違反 |
| `401 Unauthorized` | 未認証・認証失敗 |
| `403 Forbidden` | 認証済みだが権限がない（他人のリソース操作） |
| `404 Not Found` | リソースが存在しない、**または論理削除済み** |
| `409 Conflict` | 一意制約違反（メール・ユーザー名の重複） |
| `413 Payload Too Large` | ファイルサイズ超過 |
| `415 Unsupported Media Type` | 非対応のファイル形式 |
| `500 Internal Server Error` | 予期しないエラー |

> **401 と 403 の使い分け**: 「あなたが誰か分からない」= 401、「あなたが誰かは分かるが、それをする権限がない」= 403。
> 他人の投稿を削除しようとした場合は **403**。存在しない投稿を削除しようとした場合は **404**。

---

## 2. ページネーション設計

### 2.1 カーソルベース（Keyset Pagination）を採用

**タイムライン・コメント一覧・ユーザー一覧はすべてカーソル方式とする。オフセット方式は使わない。**

#### オフセット方式を避ける理由

| 問題 | 内容 |
|---|---|
| **重複・欠落が必ず発生する** | `LIMIT 20 OFFSET 20` を投げる間に新しい投稿が1件入ると、全体が1つずれ、2ページ目の先頭が1ページ目の末尾と**重複**する。逆に削除されると1件**欠落**する |
| **深いページで劣化する** | `OFFSET 10000` はスキップする1万行を実際に読む必要があり、コストが線形に増える |

タイムラインは**新着が絶えず挿入される**性質を持つため、無限スクロールUIとオフセット方式の組み合わせは致命的である。カーソル方式なら「この投稿より古いものを20件」という指定になるため、間に何件挿入されても影響を受けない。

#### カーソルの仕様

カーソルは `(created_at, id)` の複合キーをJSON化してBase64エンコードした**不透明な文字列**とする。

```
{"c":"2026-08-17T12:34:56Z","i":1234}
        ↓ Base64
eyJjIjoiMjAyNi0wOC0xN1QxMjozNDo1NloiLCJpIjoxMjM0fQ==
```

| 項目 | 内容 |
|---|---|
| リクエスト | `?limit=20&cursor=<カーソル文字列>`（初回は `cursor` を省略） |
| `limit` | 省略時20、最大50 |
| レスポンス | `{ items, nextCursor, hasNext }` |
| 不正なカーソル | `400 VALIDATION_ERROR` |

> **クライアントはカーソルの中身を解釈してはならない。** 不透明な文字列として次のリクエストにそのまま渡すだけにする。将来カーソルの構造を変えても、クライアントを変更せずに済む。

#### `id` をタイブレーカーとして必ず含める理由

`created_at` だけをカーソルにすると、**同一時刻の投稿が複数あった場合に取りこぼす**。

```
投稿A: created_at = 12:00:00, id = 100
投稿B: created_at = 12:00:00, id = 101   ← 同じ時刻
```

`created_at < '12:00:00'` で次ページを取ると、A と B の両方が漏れる。
`(created_at, id) < ('12:00:00', 101)` なら A だけが次ページに含まれ、正しく連続する。

[04_data_model.md](04_data_model.md) のインデックスも `(created_at DESC, id DESC)` と複合になっているのはこのため。

### 2.2 例外: ユーザー検索はオフセット方式

| 機能 | 方式 | 理由 |
|---|---|---|
| タイムライン、コメント、フォロー一覧、いいね一覧 | **カーソル** | 新着挿入で順序が動く |
| **ユーザー検索（#20、Phase2）** | **オフセット** | 検索結果は安定しており、ページ番号UIが自然。「3ページ目に飛ぶ」操作もできる |

> **ページネーション方式は機能ごとに選ぶもの**であり、アプリ全体で統一する必要はない。この使い分けの判断自体が学習ポイントである。

---

## 3. エンドポイント一覧

| # | メソッド | パス | 認証 | 機能ID | 概要 |
|---|---|---|---|---|---|
| 1 | `POST` | `/auth/signup` | 不要 | F-AU-01 | 新規登録（＋自動ログイン） |
| 2 | `POST` | `/auth/login` | 不要 | F-AU-02 | ログイン |
| 3 | `GET` | `/auth/me` | 必要 | F-AU-04 | 現在のユーザー情報取得 |
| 4 | `PUT` | `/auth/password` | 必要 | F-AU-05 | パスワード変更（Phase2） |
| 27 | `POST` | `/auth/refresh` | **不要** | F-AU-06 | トークン再発行（アクセストークンの期限切れ時） |
| 28 | `POST` | `/auth/logout` | 必要 | F-AU-03 | ログアウト（リフレッシュトークンの失効） |
| 5 | `GET` | `/timeline` | 必要 | F-TL-01〜03 | タイムライン取得 |
| 6 | `POST` | `/posts` | 必要 | F-PO-01, F-PO-02 | 投稿作成 |
| 7 | `GET` | `/posts/{postId}` | 必要 | F-PO-03 | 投稿詳細取得 |
| 8 | `PATCH` | `/posts/{postId}` | 必要 | F-PO-04 | 投稿編集（Phase2） |
| 9 | `DELETE` | `/posts/{postId}` | 必要 | F-PO-05 | 投稿削除 |
| 10 | `GET` | `/posts/{postId}/comments` | 必要 | F-CM-02 | コメント一覧取得 |
| 11 | `POST` | `/posts/{postId}/comments` | 必要 | F-CM-01 | コメント投稿 |
| 12 | `PATCH` | `/comments/{commentId}` | 必要 | F-CM-03 | コメント編集（Phase2） |
| 13 | `DELETE` | `/comments/{commentId}` | 必要 | F-CM-04 | コメント削除 |
| 14 | `PUT` | `/posts/{postId}/like` | 必要 | F-LK-01 | いいね（冪等） |
| 15 | `DELETE` | `/posts/{postId}/like` | 必要 | F-LK-02 | いいね解除（冪等） |
| 16 | `GET` | `/posts/{postId}/likes` | 必要 | F-LK-04 | いいねしたユーザー一覧（Phase2） |
| 17 | `GET` | `/users/{userId}` | 必要 | F-US-01, F-US-02 | プロフィール取得 |
| 18 | `GET` | `/users/{userId}/posts` | 必要 | F-US-02 | ユーザーの投稿一覧 |
| 19 | `PATCH` | `/users/me` | 必要 | F-US-03, F-US-04 | プロフィール編集 |
| 20 | `GET` | `/users` | 必要 | F-US-05 | ユーザー検索（Phase2） |
| 21 | `PUT` | `/users/{userId}/follow` | 必要 | F-FL-01 | フォロー（冪等） |
| 22 | `DELETE` | `/users/{userId}/follow` | 必要 | F-FL-02 | フォロー解除（冪等） |
| 23 | `GET` | `/users/{userId}/following` | 必要 | F-FL-03 | フォロー中一覧（Phase2） |
| 24 | `GET` | `/users/{userId}/followers` | 必要 | F-FL-04 | フォロワー一覧（Phase2） |
| 25 | `POST` | `/files` | 必要 | F-IM-01, F-IM-03 | 画像アップロード |
| 26 | `GET` | `/files/{fileId}` | 不要 | F-IM-02 | 画像配信 |

> **#27 / #28 は後から追加した認証エンドポイント**（[09_decision_log.md](09_decision_log.md) D-29）。認証系なので表では #4 の直後に置いているが、**番号は末尾の続き**にしている。既存の番号を振り直さないため（CLAUDE.md 7.4「IDは一度振ったら変更しない」）。

### 3.1 設計上の重要な判断

#### ① いいね・フォローに `PUT` / `DELETE` を使う理由

```
✕  POST /posts/1/like    +  POST /posts/1/unlike
○  PUT  /posts/1/like    +  DELETE /posts/1/like
```

操作対象のリソースは「**その投稿に対する自分のいいね**」であり、`PUT` は「その状態にする」という**冪等な操作**として自然に対応する。

| メリット | 内容 |
|---|---|
| 冪等性が仕様として明確 | `PUT` は「何回実行しても同じ結果」がHTTPの規約。二重クリックで409を返す必要がない |
| URLがリソースを表す | `/posts/1/like` という1つのリソースに対して、作成（PUT）と削除（DELETE）を行う |
| クライアントが単純になる | 楽観的UI更新で連打されても、サーバー側でエラーにならない |

**実装**: 既にいいね済みの状態で `PUT` が来たら、DBのUNIQUE制約違反を捕捉して**カウンタを更新せずに200を返す**（[04_data_model.md](04_data_model.md) 設計判断⑥）。

#### ② 画像アップロードを投稿作成から分離する理由

```
✕  POST /posts (multipart: body + image)
○  POST /files (multipart: image) → fileId → POST /posts (json: body + imageFileIds)
```

| メリット | 内容 |
|---|---|
| 投稿APIをJSONに保てる | multipartとJSONが混在せず、リクエスト/レスポンスの設計が単純になる |
| 体感速度が上がる | ユーザーが本文を入力している間に、裏で画像アップロードを完了できる |
| リトライが独立する | アップロードだけ失敗した場合、本文を再入力させずに済む |
| 基盤を再利用できる | プロフィール画像（F-US-04）も同じ `POST /files` を使える |

**デメリット**: 投稿せずに離脱した場合、アップロード済みファイルが**孤児**になる（[01_requirements.md](01_requirements.md) R-03）。MVPでは許容し、Phase3で定期クリーンアップバッチを検討する。検出クエリは [04_data_model.md](04_data_model.md) 3.5に記載済み。

#### ③ 更新系に `PATCH` を使う理由

投稿編集もプロフィール編集も**部分更新**（送られたフィールドだけを更新）であるため `PATCH` を使う。
`PUT` は「リソース全体を置き換える」意味になり、送らなかったフィールドをnullにすべきかが曖昧になる。

例外は `PUT /auth/password`。パスワードは「全体を置き換える」操作であり、部分更新の概念がないため `PUT` が適切。

#### ④ `isLikedByMe` / `isFollowing` をレスポンスに含める

これらは「**リクエストしたユーザーから見た状態**」であり、DBのカラムには存在しない。
UIの表示（いいねボタンの塗りつぶし、フォローボタンのラベル）に必須なので、サーバー側で組み立てて返す。

**N+1問題に注意**: タイムライン20件それぞれに個別クエリを投げてはならない。
取得した投稿IDをまとめて**1回のクエリ**で引くこと（[04_data_model.md](04_data_model.md) 5.3）。

```sql
SELECT post_id FROM likes WHERE user_id = :me AND post_id = ANY(:postIds);
```

---

## 4. レスポンススキーマ

**本章がレスポンス構造の正**とする。DBのカラム定義は [04_data_model.md](04_data_model.md) が正であり、両者は必ずしも1対1ではない。

### `UserSummary` — ユーザーの要約（一覧・投稿者情報用）

```json
{
  "id": 1,
  "username": "taro_123",
  "displayName": "たろう",
  "avatarUrl": "http://localhost:8080/api/v1/files/10"
}
```

`avatarUrl` は未設定なら `null`。**DBには保存せず、`FileStorageService` が組み立てる**（[04_data_model.md](04_data_model.md) 設計判断⑤）。

### `UserListItem` — ユーザー一覧の1行（`UserSummary` の拡張）

「ユーザー行」コンポーネント（[03_screen_design.md](03_screen_design.md)）を表示するための型。**SC-07 / SC-08 / SC-09 / SC-10 で共通**に使う。

```json
{
  "id": 1,
  "username": "taro_123",
  "displayName": "たろう",
  "avatarUrl": "http://localhost:8080/api/v1/files/10",
  "bio": "学習目的でSNSアプリを作っています。",
  "isFollowing": false,
  "isMe": false
}
```

| フィールド | 説明 |
|---|---|
| `bio` | 一覧に1行だけ表示する。未設定なら `null` |
| `isFollowing` | リクエストユーザーがこのユーザーをフォロー済みか。**行内のフォローボタンの状態に使う** |
| `isMe` | この行がリクエストユーザー自身か。`true` ならフォローボタンを出さない |

> **`UserSummary` と分ける理由**: `UserSummary` は投稿の `author` としても使われる。投稿カードに `bio` や `isFollowing` は不要であり、そこに含めると**タイムライン20件ぶんのフォロー判定クエリが無駄に走る**。用途ごとに型を分ける。

> **`isFollowing` の取得は必ず一括で行う**（[04_data_model.md](04_data_model.md) 6.6）。`isLikedByMe` と同じN+1パターン。

### `UserProfile` — プロフィール画面用（`UserSummary` の拡張）

```json
{
  "id": 1,
  "username": "taro_123",
  "displayName": "たろう",
  "avatarUrl": "http://localhost:8080/api/v1/files/10",
  "bio": "学習目的でSNSアプリを作っています。",
  "postCount": 56,
  "followingCount": 12,
  "followerCount": 34,
  "isFollowing": false,
  "isMe": false,
  "createdAt": "2026-08-01T00:00:00Z"
}
```

| フィールド | 説明 |
|---|---|
| `postCount` | 論理削除済みを除いた投稿数 |
| `isFollowing` | リクエストユーザーがこのユーザーをフォローしているか。`isMe: true` の場合は常に `false` |
| `isMe` | このプロフィールがリクエストユーザー自身か。SC-05の表示分岐に使う |

### `PostSummary` — 投稿（タイムライン・詳細で共通）

```json
{
  "id": 100,
  "author": {
    "id": 1,
    "username": "taro_123",
    "displayName": "たろう",
    "avatarUrl": "http://localhost:8080/api/v1/files/10"
  },
  "body": "今日はいい天気でした。",
  "images": [
    {
      "fileId": 20,
      "url": "http://localhost:8080/api/v1/files/20",
      "width": 1200,
      "height": 800
    }
  ],
  "likeCount": 12,
  "commentCount": 3,
  "isLikedByMe": true,
  "createdAt": "2026-08-17T12:00:00Z",
  "editedAt": null
}
```

| フィールド | 説明 |
|---|---|
| `images` | 添付画像の配列。なければ空配列 `[]`（`null` にしない） |
| `width` / `height` | フロントが画像読み込み前に領域を確保するために使う（レイアウトシフト防止） |
| `editedAt` | `null` でなければUIに「編集済み」を表示 |

> **`impressionCount` のようなフィールドは存在しない。** インプレッション数を表示しないという差別化ポイント（[01_requirements.md](01_requirements.md) 2.2）はAPIレベルでも徹底する。DBにも閲覧ログを持たない。

### `Comment`

```json
{
  "id": 500,
  "author": { "id": 2, "username": "hanako", "displayName": "はなこ", "avatarUrl": null },
  "body": "いいですね！",
  "isMine": true,
  "createdAt": "2026-08-17T13:00:00Z",
  "editedAt": null
}
```

`isMine` は `[⋯]` メニュー（編集・削除）の表示判定に使う。

### `CursorPage<T>` — カーソルページネーションの共通ラッパー

```json
{
  "items": [ /* T の配列 */ ],
  "nextCursor": "eyJjIjoiMjAyNi0wOC0xN1QxMjowMDowMFoiLCJpIjoxMDB9",
  "hasNext": true
}
```

`hasNext: false` のとき `nextCursor` は `null`。

### `OffsetPage<T>` — オフセットページネーション（#20のみ）

```json
{
  "items": [ /* T の配列 */ ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8
}
```

---

## 5. エンドポイント詳細

### #1 `POST /auth/signup` — 新規登録

**認証**: 不要 / **機能**: F-AU-01 / **画面**: SC-02

**リクエスト**
```json
{
  "email": "taro@example.com",
  "username": "taro_123",
  "displayName": "たろう",
  "password": "Password1"
}
```

**バリデーション**

| フィールド | ルール |
|---|---|
| `email` | 必須、メール形式、255文字以内 |
| `username` | 必須、3〜30文字、`^[a-zA-Z0-9_]+$` |
| `displayName` | 必須、1〜50文字 |
| `password` | 必須、8文字以上、英字と数字を各1文字以上 |

**レスポンス `201 Created`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9....",
  "refreshToken": "vxThD252Pqr0CDPbWK3S9z8C17-m-NhhM2cozXinYtY",
  "expiresIn": 900,
  "user": { "id": 1, "username": "taro_123", "displayName": "たろう", "avatarUrl": null }
}
```

**エラー**: `400 VALIDATION_ERROR` / `409 EMAIL_ALREADY_EXISTS` / `409 USERNAME_ALREADY_EXISTS`

> **登録と同時にトークンを返す**ことで、クライアントはログイン画面を経由せずSC-03へ遷移できる（F-AU-01）。`expiresIn` はアクセストークンの有効秒数（900秒＝15分）で、クライアントが再発行のタイミングを判断するために返す（JWTを自前でデコードさせないため）。

---

### #2 `POST /auth/login` — ログイン

**認証**: 不要 / **機能**: F-AU-02 / **画面**: SC-01

**リクエスト**
```json
{ "email": "taro@example.com", "password": "Password1" }
```

**レスポンス `200 OK`**: #1と同じ形式（`accessToken` + `refreshToken` + `expiresIn` + `user`）

**エラー**: `401 INVALID_CREDENTIALS`

> **メールが存在しない場合とパスワードが違う場合で、レスポンスを区別しない。** どちらも `INVALID_CREDENTIALS` を返す。アカウントの存在を推測させないため。

---

### #3 `GET /auth/me` — 現在のユーザー情報

**認証**: 必要 / **機能**: F-AU-04 / **画面**: 全画面（起動時）

**レスポンス `200 OK`**: `UserSummary`

**用途**: アプリ起動時にJWTの有効性を検証し、ユーザー情報を復元する。401が返ればトークンを破棄してSC-01へ遷移する。

---

### #4 `PUT /auth/password` — パスワード変更（Phase2）

**認証**: 必要 / **機能**: F-AU-05 / **画面**: SC-11

**リクエスト**
```json
{ "currentPassword": "Password1", "newPassword": "NewPassword2" }
```

**レスポンス `204 No Content`**
**エラー**: `400 VALIDATION_ERROR` / `401 INVALID_CREDENTIALS`（現在のパスワードが違う）

> **Phase2で実装する際は、パスワード変更時にリフレッシュトークンを全失効させること**（`refreshTokenService.revokeAll(userId)`）。パスワードを変えた理由が「漏洩したかもしれない」である以上、他端末のセッションを生かしたままにするのは危険なため。**当初の「既存のJWTは有効なまま」という記述は、リフレッシュトークン導入（D-29）により見直した。** なお発行済みアクセストークンは最大15分間有効なままである（ステートレスJWTの性質）。

---

### #27 `POST /auth/refresh` — トークン再発行

**認証**: **不要** / **機能**: F-AU-06 / **画面**: 全画面（401を受けたとき）

**リクエスト**
```json
{ "refreshToken": "vxThD252Pqr0CDPbWK3S9z8C17-m-NhhM2cozXinYtY" }
```

**レスポンス `200 OK`**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9....",
  "refreshToken": "sKKeOeyoFZNIx8Qw3rTvB2mNpLcJ7dYhGa4Ue1KsZoM",
  "expiresIn": 900
}
```

**エラー**: `400 VALIDATION_ERROR`（未指定）/ `401 INVALID_REFRESH_TOKEN`

> **`user` を含まない。** 再発行はトークンの差し替えだけが目的で、ユーザー情報が必要なら `GET /auth/me` を呼べばよいため。

> **なぜ認証不要なのか**: アクセストークンが期限切れになった状態で呼ぶAPIだから。有効なアクセストークンを要求したら、そもそも再発行する必要がない。**ボディのリフレッシュトークンそのものが認証情報になる。**

> **返ってきた `refreshToken` は必ず保存し直すこと（ローテーション）。** 送った側のトークンはこの時点で使用済みになり、二度と使えない。古い値を持ち続けて再送すると、盗用とみなされて**そのログインのトークンがすべて失効する**（＝再ログインになる）。

**`401 INVALID_REFRESH_TOKEN` を返す条件**（**理由は区別しない**）

| # | 条件 |
|---|---|
| 1 | そのトークンが存在しない（でたらめな値） |
| 2 | 有効期限（14日）を過ぎている |
| 3 | 既に使用済み → **さらにファミリー全体を失効させる**（盗用検知） |
| 4 | 失効済み（ログアウト、または盗用検知の巻き添え） |
| 5 | 持ち主のユーザーが論理削除されている |

> **理由を返さないのは意図的。** 「存在しない」と「失効済み」を区別すると、攻撃者に「そのトークンは実在する」という情報を与えてしまう。

---

### #28 `POST /auth/logout` — ログアウト

**認証**: **必要** / **機能**: F-AU-03 / **画面**: 全画面（ヘッダーのメニュー）

**リクエスト**: ボディなし

**レスポンス `204 No Content`**

**エラー**: `401 UNAUTHENTICATED`（アクセストークンが無効）

> **なぜ認証が必要で、リフレッシュトークンをボディで受け取らないのか**: 「誰のトークンを失効させるか」をアクセストークンの `sub` から決めるため。ボディで受け取る方式にすると、**他人のリフレッシュトークンを送りつけて強制ログアウトさせる**妨害が成立してしまう。

> **そのユーザーの全リフレッシュトークンを失効させる。** 他端末のログインも切れる。MVPでは端末ごとのセッション管理をしないため（[01_requirements.md](01_requirements.md) 3.2）。

> **⚠️ 発行済みのアクセストークンは失効しない。** ステートレスJWTの本質的な性質で、最大15分間は使えてしまう。**クライアントは必ず localStorage の両トークンを破棄すること。** サーバー側の失効はあくまで「延長させない」ための措置である（[09_decision_log.md](09_decision_log.md) D-29）。

---
---

### #5 `GET /timeline` — タイムライン取得

**認証**: 必要 / **機能**: F-TL-01, F-TL-02, F-TL-03 / **画面**: SC-03

**クエリパラメータ**

| 名前 | 必須 | デフォルト | 説明 |
|---|---|---|---|
| `tab` | — | `all` | `all`（全体） / `following`（フォロー中） |
| `limit` | — | `20` | 1〜50 |
| `cursor` | — | — | 前回のレスポンスの `nextCursor` |

**レスポンス `200 OK`**: `CursorPage<PostSummary>`

```json
{
  "items": [ /* PostSummary の配列、新着順 */ ],
  "nextCursor": "eyJjIjoi...",
  "hasNext": true
}
```

**挙動**

| `tab` | 対象 |
|---|---|
| `all` | 全ユーザーの投稿（論理削除を除く） |
| `following` | **自分＋フォロー中ユーザー**の投稿 |

SQLは [04_data_model.md](04_data_model.md) 5.1 / 5.2 を参照。

---

### #6 `POST /posts` — 投稿作成

**認証**: 必要 / **機能**: F-PO-01, F-PO-02 / **画面**: MD-01

**リクエスト**
```json
{
  "body": "今日はいい天気でした。",
  "imageFileIds": [20]
}
```

**バリデーション**

| フィールド | ルール |
|---|---|
| `body` | **必須**、1〜280文字、空白のみ不可 |
| `imageFileIds` | 任意、配列。**MVPは最大1件**（Phase2で4件）。自分がアップロードしたファイルのIDのみ指定可 |

**レスポンス `201 Created`**: `PostSummary`

**エラー**: `400 VALIDATION_ERROR` / `404 NOT_FOUND`（存在しない `fileId`）/ `403 FORBIDDEN`（他人がアップロードしたファイルを指定）

> **`imageFileIds` の所有者チェックを忘れないこと。** 他人のアップロードしたファイルIDを指定して自分の投稿に添付できてしまうと、意図しない画像の流用が起きる。

---

### #7 `GET /posts/{postId}` — 投稿詳細取得

**認証**: 必要 / **機能**: F-PO-03 / **画面**: SC-04

**レスポンス `200 OK`**: `PostSummary`
**エラー**: `404 NOT_FOUND`（存在しない、**または論理削除済み**）

---

### #8 `PATCH /posts/{postId}` — 投稿編集（Phase2）

**認証**: 必要 / **機能**: F-PO-04 / **画面**: MD-02

**リクエスト**
```json
{ "body": "今日はいい天気でした。（修正）" }
```

**レスポンス `200 OK`**: `PostSummary`（`editedAt` に現在時刻が入る）
**エラー**: `400` / `403 FORBIDDEN`（自分の投稿ではない）/ `404 NOT_FOUND`

> **画像の変更は不可。** `imageFileIds` を送っても無視する（[02_feature_list.md](02_feature_list.md) F-PO-04の制限）。

---

### #9 `DELETE /posts/{postId}` — 投稿削除

**認証**: 必要 / **機能**: F-PO-05 / **画面**: MD-03

**レスポンス `204 No Content`**
**エラー**: `403 FORBIDDEN`（自分の投稿ではない）/ `404 NOT_FOUND`

**処理内容**: `UPDATE posts SET deleted_at = now() WHERE id = ?`（論理削除）
コメント・いいね・画像は**追い削除しない**（[04_data_model.md](04_data_model.md) 設計判断②）。

---

### #10 `GET /posts/{postId}/comments` — コメント一覧

**認証**: 必要 / **機能**: F-CM-02, F-TL-03 / **画面**: SC-04

**クエリパラメータ**: `limit`（既定20）、`cursor`

**レスポンス `200 OK`**: `CursorPage<Comment>`

> **並び順は古い順（昇順）。** タイムラインの新着順とは逆である。会話の流れを追いやすくするため（SC-04の仕様）。
> カーソルの比較も `(created_at, id) > (cursor)` と**不等号の向きが逆**になる点に注意。

---

### #11 `POST /posts/{postId}/comments` — コメント投稿

**認証**: 必要 / **機能**: F-CM-01 / **画面**: SC-04

**リクエスト**
```json
{ "body": "いいですね！" }
```

**レスポンス `201 Created`**
```json
{
  "comment": { /* Comment */ },
  "commentCount": 4
}
```

**エラー**: `400 VALIDATION_ERROR` / `404 NOT_FOUND`（投稿が存在しない）

> **`commentCount` を返す理由**: クライアントが投稿カードのコメント数を即座に更新できる。再取得のリクエストを省ける。
> **同一トランザクションで `posts.comment_count` を +1 する**（[04_data_model.md](04_data_model.md) 3.1 実装ルール①）。

---

### #12 `PATCH /comments/{commentId}` — コメント編集（Phase2）

**認証**: 必要 / **機能**: F-CM-03

**リクエスト**: `{ "body": "..." }` / **レスポンス `200 OK`**: `Comment`
**エラー**: `403 FORBIDDEN` / `404 NOT_FOUND`

---

### #13 `DELETE /comments/{commentId}` — コメント削除

**認証**: 必要 / **機能**: F-CM-04 / **画面**: MD-03

**レスポンス `200 OK`**
```json
{ "commentCount": 3 }
```

**エラー**: `403 FORBIDDEN` / `404 NOT_FOUND`

> **204ではなく200を返す理由**: 更新後の `commentCount` をクライアントに返す必要があるため。
> **同一トランザクションで `posts.comment_count` を -1 する**（[04_data_model.md](04_data_model.md) 3.2 非対称ルール）。

---

### #14 `PUT /posts/{postId}/like` — いいね

**認証**: 必要 / **機能**: F-LK-01 / **画面**: SC-03, SC-04

**リクエスト**: ボディなし

**レスポンス `200 OK`**
```json
{ "likeCount": 13, "isLikedByMe": true }
```

**エラー**: `404 NOT_FOUND`（投稿が存在しない、または論理削除済み）

**冪等性**: 既にいいね済みの状態で呼ばれた場合も **`200 OK` を返す**。カウンタは増やさない。
UNIQUE制約違反（`DataIntegrityViolationException`）を捕捉して実現する。

---

### #15 `DELETE /posts/{postId}/like` — いいね解除

**認証**: 必要 / **機能**: F-LK-02

**レスポンス `200 OK`**
```json
{ "likeCount": 12, "isLikedByMe": false }
```

**冪等性**: いいねしていない状態で呼ばれても `200 OK`。カウンタは減らさない。

> **204ではなく200を返す理由**: #13と同様、更新後のカウントを返すため。

---

### #16 `GET /posts/{postId}/likes` — いいねしたユーザー一覧（Phase2）

**認証**: 必要 / **機能**: F-LK-04 / **画面**: SC-10

**レスポンス `200 OK`**: `CursorPage<UserListItem>`（いいねした新しい順）

> SC-10も「ユーザー行」コンポーネントを使うため、`UserListItem` に揃える。

---

### #17 `GET /users/{userId}` — プロフィール取得

**認証**: 必要 / **機能**: F-US-01, F-US-02 / **画面**: SC-05

**レスポンス `200 OK`**: `UserProfile`
**エラー**: `404 NOT_FOUND`

> `isMe` と `isFollowing` はリクエストユーザーとの関係から算出する。SC-05はこの2つで表示を切り替える。

---

### #18 `GET /users/{userId}/posts` — ユーザーの投稿一覧

**認証**: 必要 / **機能**: F-US-02, F-TL-03 / **画面**: SC-05

**クエリパラメータ**: `limit`、`cursor`
**レスポンス `200 OK`**: `CursorPage<PostSummary>`（新着順、論理削除を除く）

---

### #19 `PATCH /users/me` — プロフィール編集

**認証**: 必要 / **機能**: F-US-03, F-US-04 / **画面**: SC-06

**リクエスト**（送られたフィールドのみ更新）
```json
{
  "displayName": "たろう（改）",
  "bio": "SNSアプリ開発中",
  "avatarFileId": 30
}
```

| フィールド | ルール |
|---|---|
| `displayName` | 1〜50文字 |
| `bio` | 160文字以内。`null` を明示的に送れば削除 |
| `avatarFileId` | 自分がアップロードしたファイルのみ。`null` で削除（Phase2） |

**レスポンス `200 OK`**: `UserProfile`
**エラー**: `400 VALIDATION_ERROR` / `403 FORBIDDEN`（他人のファイルID）

> **`email` と `username` は変更できない**（SC-06の仕様）。送られても無視する。

---

### #20 `GET /users` — ユーザー検索（Phase2）

**認証**: 必要 / **機能**: F-US-05 / **画面**: SC-07

**クエリパラメータ**

| 名前 | 必須 | デフォルト | 説明 |
|---|---|---|---|
| `q` | ○ | — | 検索キーワード。**1〜50文字** |
| `page` | — | `0` | 0始まり |
| `size` | — | `20` | 最大50 |

**レスポンス `200 OK`**: `OffsetPage<UserListItem>`

**エラー**

| コード | 条件 |
|---|---|
| `400 VALIDATION_ERROR` | `q` が空、または50文字超 |

**検索の仕様**

| 項目 | 内容 |
|---|---|
| 検索対象 | `username` と `display_name` の2つ。**`bio` と `email` は対象外** |
| 一致方法 | **第1段階: 前方一致**（`q%`） → **第2段階: pg_trgm によるあいまい一致**。段階式に拡張する |
| 大文字小文字 | 区別しない |
| 除外対象 | 論理削除済みユーザー、**リクエスト元のユーザー自身** |
| 並び順 | ユーザー名の一致を優先 → 一致度 → `id`（**タイブレーカー必須**） |

> **唯一のオフセット方式のエンドポイント**（2.2参照）。
> **検索方式の詳細・SQL・インデックス・セキュリティ要求は [04_data_model.md](04_data_model.md) 6章が正。**

> **`ORDER BY` に `id` を必ず含める。** オフセット方式でも並び順が不定だと、2ページ目に1ページ目と同じユーザーが出たり取りこぼしたりする。カーソル方式のタイブレーカー（2.1）と同じ問題である。

> **`isFollowing` の取得でN+1を作らないこと。** 検索結果のユーザーIDをまとめて1回のクエリで引く（[04_data_model.md](04_data_model.md) 6.6）。`isLikedByMe` とまったく同じパターン。

---

### #21 `PUT /users/{userId}/follow` — フォロー

**認証**: 必要 / **機能**: F-FL-01 / **画面**: SC-05

**リクエスト**: ボディなし

**レスポンス `200 OK`**
```json
{ "isFollowing": true, "followerCount": 35 }
```

**エラー**

| コード | 条件 |
|---|---|
| `400 SELF_FOLLOW_NOT_ALLOWED` | `userId` が自分自身 |
| `404 NOT_FOUND` | 対象ユーザーが存在しない |

**冪等性**: 既にフォロー済みでも `200 OK`。カウントは増やさない。

> `followerCount` は**フォローされた側（`userId`）のフォロワー数**。SC-05の表示更新に使う。

---

### #22 `DELETE /users/{userId}/follow` — フォロー解除

**認証**: 必要 / **機能**: F-FL-02

**レスポンス `200 OK`**: `{ "isFollowing": false, "followerCount": 34 }`
**冪等性**: フォローしていなくても `200 OK`

---

### #23 `GET /users/{userId}/following` — フォロー中一覧（Phase2）
### #24 `GET /users/{userId}/followers` — フォロワー一覧（Phase2）

**認証**: 必要 / **機能**: F-FL-03, F-FL-04 / **画面**: SC-08, SC-09

**レスポンス `200 OK`**: `CursorPage<UserListItem>`（フォローした新しい順）

`isFollowing` により、一覧上でそのままフォロー操作ができる。**一覧のユーザーIDをまとめて1回で引くこと**（[04_data_model.md](04_data_model.md) 6.6）。

---

### #25 `POST /files` — 画像アップロード

**認証**: 必要 / **機能**: F-IM-01, F-IM-03 / **画面**: MD-01, SC-06

**リクエスト**: `multipart/form-data`

| パート名 | 内容 |
|---|---|
| `file` | 画像ファイル |

**バリデーション（F-IM-03）**

| 項目 | ルール | エラー |
|---|---|---|
| 形式 | `image/jpeg` / `image/png` / `image/webp` | `415 UNSUPPORTED_MEDIA_TYPE` |
| サイズ | 5MB以下 | `413 FILE_TOO_LARGE` |
| 実体 | **拡張子だけでなくファイルの先頭バイト（マジックナンバー）も検証する** | `415 UNSUPPORTED_MEDIA_TYPE` |

**レスポンス `201 Created`**
```json
{
  "fileId": 20,
  "url": "http://localhost:8080/api/v1/files/20",
  "width": 1200,
  "height": 800
}
```

> **拡張子だけの検証では不十分。** `.jpg` にリネームした実行ファイルを弾くため、先頭バイトを確認すること（[06_non_functional.md](06_non_functional.md) セキュリティ節）。

---

### #26 `GET /files/{fileId}` — 画像配信

**認証**: **不要** / **機能**: F-IM-02

**レスポンス `200 OK`**: 画像バイナリ（`Content-Type` は `stored_files.content_type`）

| ヘッダー | 値 |
|---|---|
| `Cache-Control` | `public, max-age=31536000, immutable` |

> **認証を不要にする理由**: `<img src="...">` タグは `Authorization` ヘッダーを付けられない。
> 本アプリの投稿は全ログインユーザーに公開されるため（非公開アカウントは対象外）、画像も公開扱いで問題ない。
> ファイルIDは連番なので推測可能だが、学習用途として許容する。厳密にやるならUUIDベースのキーか署名付きURLを使う。

> **`immutable` を付けられる理由**: ファイルの内容は一度アップロードされたら変更されない（差し替えは新しい `fileId` になる）ため。

---

## 6. シーケンス図

### 図A: ログインとJWT付与（F-AU-02）

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant R as React SPA
    participant A as Spring Boot API
    participant D as PostgreSQL

    U->>R: メールアドレスとパスワードを入力
    R->>A: POST /api/v1/auth/login
    A->>D: SELECT * FROM users WHERE email = ? AND deleted_at IS NULL
    D-->>A: user レコード
    A->>A: BCrypt.matches で照合

    alt 認証成功
        A->>A: アクセストークン生成 sub=userId exp=15分
        A->>D: INSERT INTO refresh_tokens ハッシュと family_id
        A-->>R: 200 accessToken と refreshToken と user
        R->>R: 両方のトークンを localStorage に保存
        R-->>U: SC-03 タイムラインへ遷移
    else 認証失敗 またはユーザーが存在しない
        A-->>R: 401 INVALID_CREDENTIALS
        Note over A,R: メール未登録とパスワード誤りを<br/>区別せず同じ応答にする
        R-->>U: フォーム上部にエラー表示
    end
```

### 図B: 画像付き投稿とストレージ抽象化（F-PO-02, F-IM-01）

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant R as React SPA
    participant A as API Controller
    participant S as FileStorageService
    participant L as ローカルディレクトリ
    participant D as PostgreSQL

    U->>R: 画像を選択
    R->>R: 形式とサイズをクライアント側で検証
    R->>A: POST /api/v1/files multipart
    A->>A: MIMEタイプとマジックナンバーを検証
    A->>S: store で保存を依頼
    Note over S,L: 将来 S3FileStorageService に<br/>差し替えても呼び出し側は変わらない
    S->>L: ファイル書き込み
    L-->>S: storageKey を返す
    S-->>A: storageKey
    A->>D: INSERT INTO stored_files<br/>storage_type=LOCAL storage_key=...
    D-->>A: fileId
    A-->>R: 201 fileId と url と 幅高さ
    R-->>U: プレビュー表示

    U->>R: 本文を入力して投稿ボタン
    R->>A: POST /api/v1/posts body と imageFileIds
    A->>D: BEGIN
    A->>D: fileId の所有者が自分か検証
    A->>D: INSERT INTO posts
    A->>D: INSERT INTO post_images
    A->>D: COMMIT
    A-->>R: 201 PostSummary
    R-->>U: タイムライン先頭に挿入
```

### 図C: いいねとカウンタ更新（F-LK-01）

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant R as React SPA
    participant A as API
    participant D as PostgreSQL

    U->>R: いいねボタンを押下
    R->>R: 楽観的UI更新 即座に数字を+1して塗りつぶす
    R->>A: PUT /api/v1/posts/100/like

    A->>D: BEGIN
    A->>D: INSERT INTO likes post_id user_id

    alt UNIQUE制約違反 既にいいね済み
        D-->>A: DataIntegrityViolationException
        A->>D: ROLLBACK
        A->>D: SELECT like_count FROM posts
        A-->>R: 200 現在の likeCount 冪等応答
        Note over A,R: カウンタは増やさない
    else INSERT成功
        A->>D: UPDATE posts SET like_count = like_count + 1<br/>WHERE id = 100
        Note over A,D: SQL側で相対更新する<br/>Java側で加算するとロストアップデートになる
        A->>D: COMMIT
        A-->>R: 200 likeCount と isLikedByMe
    end

    R->>R: サーバーの値でUIを確定 差異があれば補正
```

### 図D: フォロー中タイムラインのカーソルページネーション（F-TL-02, F-TL-03）

```mermaid
sequenceDiagram
    actor U as ユーザー
    participant R as React SPA
    participant A as API
    participant D as PostgreSQL

    Note over U,D: 1ページ目
    U->>R: フォロー中タブを選択
    R->>A: GET /timeline?tab=following&limit=20
    A->>D: SELECT ... WHERE user_id = me<br/>OR user_id IN フォロー中<br/>ORDER BY created_at DESC id DESC LIMIT 21
    Note over A,D: LIMIT を 21 にして<br/>次ページの有無を判定する
    D-->>A: 21件
    A->>A: 21件目を捨てて hasNext=true<br/>20件目から nextCursor を生成
    A-->>R: 200 items 20件 nextCursor hasNext
    R-->>U: 投稿カード20件を表示

    Note over U,D: 2ページ目 スクロールで下端に到達
    U->>R: 下端から200px手前に到達
    R->>R: 取得中フラグを立てて多重リクエストを防ぐ
    R->>A: GET /timeline?tab=following&limit=20<br/>&cursor=eyJjIjoi...
    A->>A: カーソルをデコードして<br/>createdAt と id を取り出す
    A->>D: SELECT ... AND created_at id < cursorCreatedAt cursorId<br/>ORDER BY created_at DESC id DESC LIMIT 21
    Note over A,D: 行値比較を使う<br/>id をタイブレーカーに含めるので<br/>同時刻の投稿も取りこぼさない
    D-->>A: 15件 21件未満
    A->>A: hasNext=false nextCursor=null
    A-->>R: 200 items 15件 hasNext=false
    R-->>U: 追記して これ以上投稿はありません を表示
```

> **`LIMIT 21` のテクニック**: 要求件数より1件多く取得し、21件返ってきたら `hasNext: true`、20件以下なら `false` と判定する。
> `COUNT(*)` で全件数を数える必要がなくなり、カーソルページネーションの利点を活かせる。

---

## 7. CORS設計

フロントエンド（React）とバックエンド（Spring Boot）が**別オリジン**で動作するため、CORSの設定が必須である。

| 項目 | 設定値 |
|---|---|
| 許可オリジン | `http://localhost:5173`（Vite開発サーバー）。**ワイルドカード `*` は使わない** |
| 許可メソッド | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` |
| 許可ヘッダー | `Authorization`, `Content-Type` |
| 公開ヘッダー | （なし） |
| `allowCredentials` | **`false`** — JWTをヘッダーで送るため、Cookieは使わない |
| プリフライトのキャッシュ | 3600秒 |

### よくあるハマりどころ

| 症状 | 原因 | 対処 |
|---|---|---|
| プリフライト（`OPTIONS`）が401になる | Spring Securityが `OPTIONS` を認証対象にしている | `OPTIONS` を認証除外にする。Springなら `CorsFilter` を認証フィルタより前に置く |
| `Authorization` ヘッダーが届かない | `allowedHeaders` に含まれていない | 明示的に許可する |
| `allowCredentials: true` にすると `*` が使えない | CORS仕様の制約 | 本アプリは `false` なので該当しないが、Cookie認証に変更する場合は注意 |
| `PATCH` だけ失敗する | `allowedMethods` に含まれていない | `PATCH` を明示的に追加する |

> **`allowCredentials: false` にできるのは、JWTを `Authorization` ヘッダーで送る設計だから。** Cookie認証にするとCSRF対策も必要になる。この判断は [09_decision_log.md](09_decision_log.md) に記録済み。

---

## 8. バリデーション規約

| 層 | 責務 |
|---|---|
| **フロントエンド** | 即時フィードバック（文字数カウンタ、必須チェック）。UXのため |
| **バックエンド** | **すべての入力を再検証する。** フロントの検証は迂回可能なため、信頼しない |
| **DB制約** | 最後の砦。アプリ層のバグがあってもデータ不整合を許さない |

**3層すべてで検証する。** どれか1つでは不十分である。

### 共通ルール

| 項目 | ルール |
|---|---|
| 文字列 | 前後の空白をトリムしてから検証する。**ただし `password` は除く**（[09_decision_log.md](09_decision_log.md) D-27） |
| 本文（`body`） | トリム後に1文字以上（空白のみの投稿を防ぐ） |
| 文字数 | **サロゲートペア（絵文字）を1文字として数える。** `String.length()` はUTF-16単位なので絵文字が2文字になる点に注意 |
| ID | 数値以外が来たら `400` |

**実装上の要点**（認証実装時に確定）:

| # | 内容 |
|---|---|
| 1 | **トリムはJacksonのデシリアライズ時に行う。** 「ボディのデシリアライズ→`@Valid`」の順で処理されるため、ここでトリムすれば検証した文字列と保存する文字列が一致する。Service層でトリムすると、検証は未トリムの値に対して行われてしまう |
| 2 | **`password` はトリムしない。** 前後の空白も正当なパスワード文字であり、黙って除去するとユーザーが正しく入力したパスワードでログインできなくなる |
| 3 | **文字数チェックに Bean Validation の `@Size` は使えない。** `@Size` は `String.length()`（UTF-16単位）で数えるため絵文字が2文字になる。PostgreSQL の `char_length` はコードポイント数なので、`@Size` を使うと**アプリとDBで数え方が食い違う**。`codePointCount` を使う独自制約を実装する |
| 4 | **1フィールドに複数の制約を重ねすぎない。** 例えばパスワードに `@Size(min=8)` と `@Pattern`（8文字以上を含む）を併記すると、短いパスワードで**エラーが2件返る**。`@Pattern` 1本にまとめる |

### レート制限

MVPでは実装しない。Phase3で必要になった場合、以下を候補とする。

| 対象 | 制限案 |
|---|---|
| `POST /auth/login` | 同一IPから5回/分（ブルートフォース対策） |
| `POST /auth/refresh` | 同一IPから20回/分（リフレッシュトークンの総当たり対策） |
| `POST /posts` | 同一ユーザーから10回/分 |
| `POST /files` | 同一ユーザーから20回/時 |

---

## 9. APIと機能・画面の対応

| # | エンドポイント | 機能ID | 画面ID |
|---|---|---|---|
| 1 | `POST /auth/signup` | F-AU-01 | SC-02 |
| 2 | `POST /auth/login` | F-AU-02 | SC-01 |
| 3 | `GET /auth/me` | F-AU-04 | 全画面 |
| 4 | `PUT /auth/password` | F-AU-05 | SC-11 |
| 27 | `POST /auth/refresh` | F-AU-06 | 全画面（401を受けたとき） |
| 28 | `POST /auth/logout` | F-AU-03 | 全画面（ヘッダーのメニュー） |
| 5 | `GET /timeline` | F-TL-01, F-TL-02, F-TL-03 | SC-03 |
| 6 | `POST /posts` | F-PO-01, F-PO-02 | MD-01 |
| 7 | `GET /posts/{id}` | F-PO-03 | SC-04 |
| 8 | `PATCH /posts/{id}` | F-PO-04 | MD-02 |
| 9 | `DELETE /posts/{id}` | F-PO-05 | MD-03 |
| 10 | `GET /posts/{id}/comments` | F-CM-02, F-TL-03 | SC-04 |
| 11 | `POST /posts/{id}/comments` | F-CM-01 | SC-04 |
| 12 | `PATCH /comments/{id}` | F-CM-03 | SC-04 |
| 13 | `DELETE /comments/{id}` | F-CM-04 | MD-03 |
| 14 | `PUT /posts/{id}/like` | F-LK-01 | SC-03, SC-04 |
| 15 | `DELETE /posts/{id}/like` | F-LK-02 | SC-03, SC-04 |
| 16 | `GET /posts/{id}/likes` | F-LK-04 | SC-10 |
| 17 | `GET /users/{id}` | F-US-01, F-US-02 | SC-05 |
| 18 | `GET /users/{id}/posts` | F-US-02 | SC-05 |
| 19 | `PATCH /users/me` | F-US-03, F-US-04 | SC-06 |
| 20 | `GET /users` | F-US-05 | SC-07 |
| 21 | `PUT /users/{id}/follow` | F-FL-01 | SC-05 |
| 22 | `DELETE /users/{id}/follow` | F-FL-02 | SC-05 |
| 23 | `GET /users/{id}/following` | F-FL-03 | SC-08 |
| 24 | `GET /users/{id}/followers` | F-FL-04 | SC-09 |
| 25 | `POST /files` | F-IM-01, F-IM-03 | MD-01, SC-06 |
| 26 | `GET /files/{id}` | F-IM-02 | SC-03, SC-04, SC-05 |

---

## 関連ドキュメント

- [02_feature_list.md](02_feature_list.md) — 機能一覧
- [03_screen_design.md](03_screen_design.md) — 画面設計
- [04_data_model.md](04_data_model.md) — データモデル（テーブル定義はこちらが正）
- [06_non_functional.md](06_non_functional.md) — 非機能要件（セキュリティ）
- [07_architecture.md](07_architecture.md) — アーキテクチャ
- [09_decision_log.md](09_decision_log.md) — 設計判断ログ
