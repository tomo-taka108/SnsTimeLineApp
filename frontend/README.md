# frontend — SnsTimeLineApp

React (SPA) + Vite + TypeScript。バックエンド（`../backend`）とは**別オリジン**で動作する。

## 起動

先にバックエンドとDBを起動しておくこと（詳細はリポジトリ直下の [README.md](../README.md)）。

```bash
docker compose up -d          # PostgreSQL（リポジトリ直下で）
cd backend && ./mvnw spring-boot:run   # :8080
cd frontend && npm install && npm run dev   # :5173
```

ブラウザで http://localhost:5173 を開く。

> **バックエンドのCORSは `http://localhost:5173` のみ許可している。** 別のポートで起動すると、APIがすべてCORSエラーで失敗する。

## 品質チェック

```bash
npx tsc --noEmit -p tsconfig.app.json   # 型チェック
npm run lint                            # oxlint
npm run build                           # 本番ビルド
```

## 構成

| ディレクトリ | 役割 |
|---|---|
| `src/api/` | APIクライアント。`client.ts` に**JWTの自動付与と401時のトークン再発行**を集約している |
| `src/auth/` | 認証状態（Context）とルートガード |
| `src/components/` | ヘッダー・トースト・フォーム部品・投稿カード・コメント表示/入力・モーダル |
| `src/hooks/` | 複数画面で共有するフック（`useLike` など） |
| `src/pages/` | 画面。ファイル名は画面ID（SC-01 など）と対応 |
| `src/pages/timeline/` | SC-03 タイムラインと専用フック（無限スクロール・新着ポーリング） |
| `src/pages/postDetail/` | SC-04 投稿詳細のコメント一覧フック（`useComments`） |
| `src/styles/` | `mockup/common.css` から移植したスタイル |
| `src/utils/` | 日時フォーマットなどの汎用ヘルパー |

## 実装済みの画面

| 画面ID | パス | 内容 |
|---|---|---|
| SC-01 | `/login` | ログイン |
| SC-02 | `/signup` | 新規登録（成功するとそのままログイン状態になる） |
| SC-03 | `/` | タイムライン。無限スクロール・タブ・新着通知バナー・投稿作成（FAB→MD-01）・いいね |
| SC-04 | `/posts/:postId` | 投稿詳細。編集(MD-02)・削除(MD-03)・いいね・コメント（投稿・表示・削除。編集はPhase2） |
| SC-12 | `*` | NotFound |

> **画像添付は未実装。** コメント編集（Phase2）も未実装で、削除のみ。

## 触るときに知っておくこと

- **401の処理を画面ごとに書かない。** `src/api/client.ts` に集約している。
- **リフレッシュトークンは使い捨て。** 同時に複数のAPIが401になっても、再発行は1回にまとめている（`refreshOnce`）。ここを崩すとバックエンドに盗用と誤検知され、**「たまに勝手にログアウトする」**という分かりにくい不具合になる。
- **`dangerouslySetInnerHTML` を使わない。** トークンを `localStorage` に置いている前提条件（[docs/09_decision_log.md](../docs/09_decision_log.md) D-07）。投稿本文もユーザー入力のため同様に扱う。
- **パスワードはトリムしない**（D-27）。`email` / `username` / `displayName` / 投稿本文はトリムする。
- **タイムラインの状態は `useTimeline` フックに一元化している。** 投稿の挿入・置換・除去がすべて同じ配列を触るため、ページに `useState` を直接並べない。コメント一覧も同様に `useComments` に一元化している（ただし古い順表示のため新規コメントは末尾に追加する）。
- **カーソルは不透明な文字列として扱う。** 中身（`{c, i}`）を組み立てたり解釈したりしない。新着件数API（`#29`）には `sinceId`（先頭投稿のid）を渡す。
- **いいねは楽観的UI更新。** `useLike` フックがクリック直後に見た目を更新し、レスポンスで実値に置き換える。失敗したら元に戻す。タイムライン・投稿詳細の両方から使う共通フック。
