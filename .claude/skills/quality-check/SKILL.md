---
description: Spring Boot（Spotless整形・Checkstyle/静的解析・JUnitテスト）と React（ESLint・Prettier・型チェック・ビルド）の品質チェックを実行する。エラーがあれば自動修正または修正して再チェックする。
allowed-tools: Bash
disable-model-invocation: false
---

# 品質チェック手順

**コミット前に必ず実施すること。** バックエンド（Spring Boot）とフロントエンド（React + Vite）で
それぞれチェックが必要。

> **実装フェーズ未着手のため、`backend/` `frontend/` はまだ存在しない。**
> 雛形を作成した時点で、以下のコマンドが実際に動くようになる。
> **ビルドツールの設定（Spotless / Checkstyle / ESLint 等）を導入した時点で、本スキルのコマンドを実際のものに更新すること。**

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

### Step 3: JUnit（自動テスト）

```bash
cd backend && ./mvnw test
```

- **すべて PASS** が目標
- テストは **Testcontainers またはテスト専用DB** を使い、開発用DB `snstimeline` を破壊しないこと

**必ず書くべきテスト**（[docs/06_non_functional.md](../../../docs/06_non_functional.md) 5.3）:

| 対象 | 確認内容 |
|---|---|
| いいねの冪等性 | 同じ投稿に2回いいねしてもカウンタが2にならない |
| いいね解除の冪等性 | いいねしていない状態で解除しても壊れない |
| コメント削除時のカウンタ | コメントを論理削除すると `comment_count` が -1 される |
| 投稿削除時のカウンタ | 投稿を論理削除しても `comment_count` は変わらない（**非対称ルール**） |
| 論理削除の除外 | 削除済み投稿がTLに出ない、GETで404になる |
| 自己フォローの拒否 | 自分をフォローすると400 |
| 他人のリソース操作 | 他人の投稿を削除すると403、存在しない投稿は404（**順序が重要**） |
| カーソルページネーション | 同一 `created_at` の投稿が2件あっても取りこぼさない |
| ファイル所有者チェック | 他人の `fileId` を指定した投稿が403になる |

---

## フロントエンド（`frontend/`）

### Step 4: ESLint（静的解析）

```bash
cd frontend && npm run lint
```

- エラー0件が目標。自動修正は `npm run lint -- --fix`

### Step 5: Prettier（フォーマット）

```bash
cd frontend && npx prettier --check .
```

- 違反がある場合は `npx prettier --write .` で修正してから再チェックする

### Step 6: 型チェック＋ビルド

```bash
cd frontend && npx tsc --noEmit    # TypeScript を採用した場合
cd frontend && npm run build
```

- **ビルドが通ること**が最低条件。型エラーを握りつぶさない

---

## 重要事項

- 品質チェックはコミット前に必ず実施する
- **バックエンド（Step 1〜3）とフロントエンド（Step 4〜6）の両方が緑**になってからコミットする
- 変更範囲がバックエンドのみ／フロントのみの場合は、該当側だけでもよい
- 設計との整合確認（[CLAUDE.md](../../../CLAUDE.md) 7.2）も併せて行うこと

### 特に見落としやすい観点

| # | 観点 | 根拠 |
|---|---|---|
| 1 | カウンタ更新が**同一トランザクション**内か。**SQL側で相対更新**しているか | D-01 |
| 2 | 全クエリに `deleted_at IS NULL` が付いているか | D-02 |
| 3 | 認可が「存在チェック→404、所有者チェック→403」の順序か | D-14 |
| 4 | `isLikedByMe` / `isFollowing` を**一括取得**してN+1を避けているか | 04 の 5.3, 6.6 |
| 5 | ログに**パスワード・JWT・メールアドレス**を出していないか | 06 の 5.2 |
| 6 | ユーザー検索で `%` `_` を**エスケープ**しているか | 04 の 6.5 |
