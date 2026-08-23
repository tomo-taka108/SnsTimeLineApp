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
| `src/components/` | ヘッダー・トースト・フォーム部品 |
| `src/pages/` | 画面。ファイル名は画面ID（SC-01 など）と対応 |
| `src/styles/` | `mockup/common.css` から移植したスタイル |

## 実装済みの画面

| 画面ID | パス | 内容 |
|---|---|---|
| SC-01 | `/login` | ログイン |
| SC-02 | `/signup` | 新規登録（成功するとそのままログイン状態になる） |
| SC-03 | `/` | **仮ページ。** ログイン中のユーザー表示のみ。投稿一覧は次回実装 |
| SC-12 | `*` | NotFound |

## 触るときに知っておくこと

- **401の処理を画面ごとに書かない。** `src/api/client.ts` に集約している。
- **リフレッシュトークンは使い捨て。** 同時に複数のAPIが401になっても、再発行は1回にまとめている（`refreshOnce`）。ここを崩すとバックエンドに盗用と誤検知され、**「たまに勝手にログアウトする」**という分かりにくい不具合になる。
- **`dangerouslySetInnerHTML` を使わない。** トークンを `localStorage` に置いている前提条件（[docs/09_decision_log.md](../docs/09_decision_log.md) D-07）。
- **パスワードはトリムしない**（D-27）。`email` / `username` / `displayName` のみトリムする。
