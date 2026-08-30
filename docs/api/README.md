# API仕様書（Swagger UI）

このディレクトリは、GitHub Pages で公開しているAPI仕様書の実体である。

**公開URL**: https://tomo-taka108.github.io/SnsTimeLineApp/api/

---

## 1. ファイルの役割

| ファイル | 役割 |
|---|---|
| `api-docs.json` | OpenAPI 定義。起動中のアプリの `/v3/api-docs` から取得したもの |
| `index.html` | CDN の Swagger UI を読み込み、`api-docs.json` を画面に描くだけの薄いHTML |

GitHub Pages 上では Java も DB も動いていない。Swagger UI は **JSON ビューア**にすぎず、
`api-docs.json` という「その時点のAPIの設計図を写し取ったファイル」を表示しているだけである。

**つまり `api-docs.json` が古いと、公開されている仕様書も古いままになる。**

---

## 2. 更新手順（APIを変更したら実施する）

`api-docs.json` は**手動更新**である。コントローラやDTOを変更したら、以下を実行して更新すること。

```powershell
# 1. DBを起動する
docker compose up -d

# 2. JWT_SECRET を設定する（ターミナルを開くたびに必要）
$env:JWT_SECRET = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))

# 3. アプリを起動する
cd backend
./mvnw spring-boot:run
```

別のターミナルで、OpenAPI 定義を取得して上書きする。

```powershell
curl http://localhost:8080/v3/api-docs -o docs/api/api-docs.json
```

差分を確認してコミットする。

```powershell
git diff docs/api/api-docs.json
git add docs/api/api-docs.json
```

> **8080番ポートが埋まっている場合**は、`./mvnw spring-boot:run "-Dspring-boot.run.arguments=--server.port=8090"`
> のように別ポートで起動し、取得先のURLも合わせて変更する。

---

## 3. ローカルで確認する

アプリを起動した状態で以下にアクセスすると、公開版と同じ内容が見られる。

```
http://localhost:8080/swagger-ui.html
```

**ローカル版では「Try it out」で実際にAPIを実行できる。**

1. `POST /api/v1/auth/signup` でテストユーザーを作る
   （メールアドレスは `example.com` ドメインを使うこと。実在のアドレスは使わない）
2. `POST /api/v1/auth/login` でログインし、`accessToken` をコピーする
3. 画面右上の **Authorize** に貼り付ける
4. 鍵アイコンの付いたAPIが実行できるようになる

> **公開版（GitHub Pages）では「Try it out」を無効にしている。**
> リクエスト先が `localhost:8080` になり、閲覧者の環境では実行できないため。

---

## 4. 仕様書が2種類ある理由

| | 役割 |
|---|---|
| **本書（Swagger UI）** | 「APIが**今どういう形か**」。実装から自動生成されるためコードと必ず一致する |
| [05_api_design.md](../05_api_design.md) | 「**なぜこの形にしたか**」。カーソルページネーションを選んだ理由、401と403の使い分け、シーケンス図など、コードには表現できない設計判断 |

両者は競合せず補完関係にある。詳しくは [09_decision_log.md](../09_decision_log.md) の D-53 を参照。

---

## 5. 今後の課題

現在の手動更新は、**API変更時に更新を忘れると仕様書が古くなる**という弱点がある。
GitHub Actions で main へのマージのたびに自動生成する案を D-52 に記載している。
