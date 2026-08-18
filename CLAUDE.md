# 開発ルール（Claude Code 用）

このファイルは Claude Code が必ず遵守するルールを定義します。
プロジェクトの詳細（概要・機能要件）は [docs/00_index.md](docs/00_index.md) を起点に参照してください。

## ⚠️ 作業開始前チェックリスト（必須）

**いかなる実装・ファイル編集も、以下をすべて確認してから開始すること。**
1つでも未完了なら、作業を止めてこのリストを満たしてから進めること。

- [ ] **Issue を作成したか？** → `gh issue create` で Issue を作り、番号を確認する
- [ ] **feature/fix/docs/chore ブランチを切ったか？** → `git checkout -b <prefix>/<Issue番号>-<内容>`
- [ ] **main ブランチに直接いないか？** → `git branch` で現在のブランチを確認する
- [ ] **コミットメッセージは Conventional Commits 形式か？** → `feat:` `fix:` `chore:` 等

> このチェックリストはルールの抜粋です。詳細は各セクションを参照してください。

---

## 1. ブランチ命名規則

ブランチを作成する際は、必ず以下のプレフィックスを使用してください。

| プレフィックス | 用途 | 例 |
|---|---|---|
| `feature/` | 新機能の実装 | `feature/12-add-follow-timeline` |
| `fix/` | バグ修正 | `fix/15-like-counter-mismatch` |
| `docs/` | ドキュメント変更のみ | `docs/1-requirements` |
| `chore/` | 設定変更・依存関係更新など | `chore/update-dependencies` |

- プレフィックスの後は英小文字・ハイフン区切りで記述する（スペース・アンダースコア禁止）
- Issue番号を含める形式を推奨：`feature/<Issue番号>-<作業内容>`

---

## 2. Issue 作成ルール（必須）

**ブランチを作成する前に、必ず GitHub Issue を作成してください。**

手順：
1. `gh issue create` コマンド、または GitHub Web UI で Issue を作成する
2. Issue 番号を確認する
3. Issue 番号をブランチ名に含める：`feature/12-add-follow-timeline`
4. ブランチを作成して作業を開始する

```bash
# Issue作成コマンド例
gh issue create --title "feat: フォロー中タイムラインを実装" --label "enhancement"
```

---

## 3. コミットメッセージ規則（Conventional Commits）

以下の形式に従ってコミットメッセージを書いてください。

```
<type>: <日本語または英語で概要を記述>
```

### type 一覧

| type | 用途 |
|---|---|
| `feat` | 新機能追加 |
| `fix` | バグ修正 |
| `docs` | ドキュメントのみの変更 |
| `style` | フォーマット変更（動作に影響しない） |
| `refactor` | リファクタリング（機能変更なし） |
| `test` | テストコードの追加・修正 |
| `chore` | ビルド・設定ファイルの変更 |

### 例

```
feat: いいね機能を追加
fix: コメント削除時のカウンタ不整合を修正
docs: ユーザー検索の設計を追記
chore: 依存パッケージを更新
```

---

## 4. プルリクエスト（PR）ルール

- **main への直接プッシュは禁止**
- 必ず `feature/` `fix/` `docs/` `chore/` ブランチを作成し、PR を通じて main にマージする
- PR タイトルはコミットメッセージと同様に Conventional Commits 形式にする

```bash
# PR作成コマンド例
gh pr create --title "docs: 要件定義書を作成" --base main
```

---

## 5. 作業の流れ（必ず守る手順）

```
1. gh issue create でIssueを作成し、Issue番号を確認する
2. git checkout -b feature/<Issue番号>-<作業内容>
3. 実装する（実装前に関連する docs/ の設計書と突き合わせる。7章参照）
4. 品質チェックを実施する（quality-check スキル。整形 / 静的解析 / テストが緑になること）
5. コミット（Conventional Commits形式）
6. git push origin feature/<ブランチ名>
7. gh pr create でPRを作成する
8. PRをマージする（マージはユーザーが行う）
9. git branch -d feature/<ブランチ名> でローカルブランチを削除する
```

---

## 6. 扱うデータに関する注意（重要）

このアプリは学習目的のSNSであり、ユーザーの**メールアドレス・パスワード**という認証情報を扱う。

- **パスワードは必ず BCrypt でハッシュ化して保存する。平文で保存しない**
- **ログ・エラーメッセージ・一時ファイルに、パスワード・JWT・メールアドレスを出力しない**（[docs/06_non_functional.md](docs/06_non_functional.md) 5.2）
- **`email` を検索対象にもAPIレスポンスにも含めない。** アカウント列挙を招く（[docs/04_data_model.md](docs/04_data_model.md) 6.5）
- サンプルデータ・テストデータには実在の個人名・実在するメールアドレスを使用しないこと（`example.com` ドメインを使う）
- `.env` と `uploads/` は**絶対にコミットしない**（`.gitignore` で除外済み）
- 技術スタック・環境構成は本ファイルではなく、要件定義書・設計書・READMEに記載する

---

## 7. 品質チェックと設計整合（実装時に必ず確認）

### 7.1 品質チェック（コミット前に必須）

実装後・コミット前に、`quality-check` スキルで整形・静的解析・テストを実行し、すべて緑にすること。
環境の起動・DBリセット・トラブル対処は `dev-environment` スキルを参照する。

> **実装フェーズ未着手のため、現時点ではビルド設定が存在しない。** バックエンド／フロントエンドの雛形を作った時点で、`quality-check` スキルのコマンドを実際のものに更新すること。

### 7.2 設計書との突き合わせ（実装前・実装中）

実装対象に関わる設計書（`docs/`）を実装前に確認し、実装が設計と一致しているか都度検証する。

- **データモデル**（[docs/04_data_model.md](docs/04_data_model.md)）: カラム名・型・制約・インデックスが設計どおりか
- **API設計**（[docs/05_api_design.md](docs/05_api_design.md)）: パス・認証要否・レスポンススキーマ・エラーコードが設計どおりか
- **画面設計**（[docs/03_screen_design.md](docs/03_screen_design.md)）: パス・表示項目・4状態（ローディング/空/エラー/正常）が設計どおりか
- **機能一覧**（[docs/02_feature_list.md](docs/02_feature_list.md)）: 機能IDと優先度（MVP / Phase2 / Phase3）を守っているか

**特に注意すべき設計上の要点**（詳細は [docs/09_decision_log.md](docs/09_decision_log.md)）:

| # | 要点 |
|---|---|
| 1 | **いいね・コメント数は非正規化カウンタ。** 登録/削除とカウンタ更新は**同一トランザクション**、かつ**SQL側で相対更新**する（D-01） |
| 2 | **リポジトリ層の全クエリに `deleted_at IS NULL` を付ける**（D-02） |
| 3 | **認可は「存在チェック→404、所有者チェック→403」の順序を守る。** 逆にすると存在有無が漏れる（D-14） |
| 4 | **N+1を作らない。** `isLikedByMe` / `isFollowing` は一括取得する（[docs/04_data_model.md](docs/04_data_model.md) 5.3, 6.6） |

### 7.3 レビュー指摘の記録

レビューで見つかった「今すぐ直さないが後で対応する」指摘は、対応漏れを防ぐため Issue 化するか関連する設計書の「今後検討」に追記する（会話ログだけに残さない）。

### 7.4 設計を変更した場合

**ドキュメントと実装の乖離を放置しない。** 設計を変えたら [docs/09_decision_log.md](docs/09_decision_log.md) に新しいIDで追記し、古い判断を「撤回」にする。**既存エントリは書き換えない**（判断の履歴自体が学習の記録になる）。

**IDは一度振ったら変更しない。** 機能ID（`F-XX-nn`）・画面ID（`SC-nn`）・判断ID（`D-nn`）のすべてに適用する。

---

## 8. ドキュメント用アセットの置き場（画像・動画）

README や設計書に貼る画像・図は、リポジトリ内の `docs/images/` に置く。

- **図・構成図**：SVG を第一候補（拡大しても鮮明・軽量・テキスト差分で修正しやすい）。例：`docs/images/infrastructure-diagram.svg`
- **スクリーンショット**：PNG を `docs/images/` に置く

**動画（画面操作の録画など）はリポジトリにコミットしない。** バイナリかつ容量が大きく、Git 履歴を肥大化させて clone を重くするため。README に載せる場合は、次のいずれかで「リポジトリ外に置いたものを参照」する。

- GitHub の Issue / PR のコメント欄に動画をドラッグ&ドロップ → 発行される URL を README から参照
- GitHub Releases にファイルとして添付し、その URL を参照

> 大容量バイナリを本格的に版管理したくなった場合は Git LFS の導入を検討する（現時点では未導入）。

---

## 9. Mermaid 図を編集した場合

`docs/` の Mermaid 図を追加・変更したら、**レンダリングを確認する**（[docs/00_index.md](docs/00_index.md) 6章の記述ルールに従う）。

```bash
npx --yes @mermaid-js/mermaid-cli -i <file>.mmd -o <file>.png
```

図を増減させた場合は、[docs/00_index.md](docs/00_index.md) 6.1 の枚数と 8章の図一覧も更新すること。
