---
description: Spring Boot（Spotless整形・静的解析・JUnitテスト）と React（oxlint・型チェック・ビルド）の品質チェックを実行する。エラーがあれば自動修正または修正して再チェックする。
allowed-tools: Bash
disable-model-invocation: false
---

# 品質チェック手順

**コミット前に必ず実施すること。** バックエンド（Spring Boot）とフロントエンド（React + Vite）で
それぞれチェックが必要。

> **`backend/`（Spring Boot 4.1.0 / JDK 25 / Maven）と `frontend/`（React 19 / Vite 8 / TypeScript）は実装済み。**
> すべてのコマンドがそのまま動く。
>
> **Step 3（JUnit）は Docker Desktop の起動が必要。** 実DBを使うテストは Testcontainers で
> `postgres:16` を起動する（[docs/09_decision_log.md](../../../docs/09_decision_log.md) D-54）。
> Docker が動いていないと `Could not find a valid Docker environment` で失敗する。

---

## バックエンド（`backend/`）

### Step 1: Spotless（コードフォーマット）

```bash
cd backend && ./mvnw spotless:check
```

- **BUILD SUCCESS** が目標
- フォーマット違反がある場合は自動修正してから再チェックする:

```bash
cd backend && ./mvnw spotless:apply && ./mvnw spotless:check
```

- `spotless:apply` はコードの動作を変えずに書式のみを修正する

### Step 2: 静的解析

```bash
cd backend && ./mvnw verify -DskipTests
```

- 型の不整合・未使用importなど、コンパイル時に検出できる問題を洗い出す
- Checkstyle / SpotBugs を導入した場合は、そのゴールもここに含める

> **JDK 25 での注意**: ビルド系プラグインは JDK 25 の javac 内部API変更に追随していないものがある。
> `NoSuchMethodError` が出たらプラグインのバージョンを疑うこと
> （実際に Spotless 2.44.4 + google-java-format が失敗し、3.10.0 / 1.36.1 へ更新して解決した）。

### Step 3: JUnit（自動テスト）

```bash
cd backend && ./mvnw test
```

- **すべて PASS** が目標
- **事前に Docker Desktop を起動しておくこと。** 実DBを使うテストは Testcontainers で
  `postgres:16` を起動する。コンテナはテスト終了時に破棄されるため、開発用DB `snstimeline` は汚れない
- テストクラスの命名は **`*Test` に統一**する。`*IT` にすると Failsafe 管轄になり `./mvnw test` で走らない
- 実DBを使うテストは `AbstractIntegrationTest` を継承する（コンテナ・JWT設定・保存先の供給を担う）

**必ず書くべきテスト**（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 5.3）:

| 対象 | 確認内容 |
|---|---|
| いいねの冪等性 | 同じ投稿に2回いいねしてもカウンタが2にならない（事前SELECT方式、D-34） |
| いいね解除の冪等性 | いいねしていない状態で解除しても壊れない |
| フォローの冪等性 | 同じユーザーを2回フォローしてもフォロワー数が2増えない（いいねと同じ事前SELECT方式、D-37） |
| フォロー解除の冪等性 | フォローしていない状態で解除しても壊れない |
| コメント削除時のカウンタ | コメントを論理削除すると `comment_count` が -1 される |
| 投稿削除時のカウンタ | 投稿を論理削除しても `comment_count` は変わらない（**非対称ルール**） |
| 論理削除の除外 | 削除済み投稿がTLに出ない、GETで404になる |
| 自己フォローの拒否 | 自分をフォローすると400（`SELF_FOLLOW_NOT_ALLOWED`） |
| 他人のリソース操作 | 他人の投稿を削除すると403、存在しない投稿は404（**順序が重要**） |
| プロフィールのカウント算出 | `postCount` / `followingCount` / `followerCount` が実データと一致する（非正規化していないため、D-36） |
| カーソルページネーション | 同一 `created_at` の投稿が2件あっても取りこぼさない |
| ファイル所有者チェック | 他人の `fileId` を指定した投稿が403になる |
| 画像のマジックバイト検証 | `.jpg` にリネームしたテキストファイルが 415 で拒否される（拡張子・Content-Typeだけを見ていないこと、D-42） |
| アップロードサイズ超過 | 5MB超が **500ではなく 413 `FILE_TOO_LARGE`** になる（Springのmultipart例外を捕捉できているか、D-42） |
| 画像配信の認証要否 | `GET /files/{id}` が**認証なしで200**、`POST /files` は認証なしで401 |
| ストレージ抽象化 | `app.storage.type` を LOCAL / S3 で切り替えても、APIのレスポンス（`url` の形）が変わらない（D-40） |

---

## フロントエンド（`frontend/`）

### Step 4: oxlint（静的解析）

```bash
cd frontend && npm run lint
```

- エラー0件が目標。自動修正は `npm run lint -- --fix`
- 設定は `frontend/.oxlintrc.json`

> **このプロジェクトは ESLint / Prettier を導入していない。** リンタは oxlint 1本で、
> `npx prettier --check .` は実行できない。

### Step 5: 型チェック＋ビルド

```bash
cd frontend && npx tsc --noEmit -p tsconfig.app.json   # 型チェック
cd frontend && npm run build                           # 型チェック＋本番ビルド
```

- **ビルドが通ること**が最低条件。型エラーを握りつぶさない

> **`-p tsconfig.app.json` を省略しないこと。** ルートの `tsconfig.json` は
> `"files": []` の**参照専用**（Project References）のため、単に `npx tsc --noEmit` と打つと
> **1ファイルも検査せずに exit 0 になる**。型エラーを含むコードが「緑」に見えてしまい、
> 品質チェックとして機能しない。
> `npm run build` は `tsc -b` で参照をたどるため、こちらは正しく検査される。

---

## 重要事項

- 品質チェックはコミット前に必ず実施する
- **バックエンド（Step 1〜3）とフロントエンド（Step 4〜5）の両方が緑**になってからコミットする
- 変更範囲がバックエンドのみ／フロントのみの場合は、該当側だけでもよい
- 設計との整合確認（[CLAUDE.md](../../../CLAUDE.md) 7.2）も併せて行うこと

### 特に見落としやすい観点

| # | 観点 | 根拠 |
|---|---|---|
| 1 | カウンタ更新が**同一トランザクション**内か。**SQL側で相対更新**しているか | D-01 |
| 2 | 全クエリに `deleted_at IS NULL` が付いているか。**MyBatis は自動除外が無いため手書き**。Mapper XML の `<include refid="activeOnly"/>` の付け忘れを疑う | D-02 / D-25 |
| 3 | 認可が「存在チェック→404、所有者チェック→403」の順序か | D-14 |
| 4 | `isLikedByMe` / `isFollowing` を**一括取得**してN+1を避けているか | 04 の 5.3, 6.6 |
| 5 | ログに**パスワード・JWT・メールアドレス**を出していないか | 06 の 5.2 |
| 6 | ユーザー検索で `%` `_` を**エスケープ**しているか | 04 の 6.5 |
| 7 | MyBatis の値の埋め込みに **`#{}` を使っているか**（`${}` は文字列置換でSQLインジェクションになる） | 06 の 3.6 / D-25 |
| 8 | MyBatis のマッピング漏れがないか。**カラム名を誤っても起動時に検出されず、値が黙って `null` になる**（JPA の `ddl-auto: validate` に相当する安全網が無い） | D-25 |
