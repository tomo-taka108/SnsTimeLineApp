---
description: k6による負荷試験（パフォーマンステスト）を実行する。専用DBの起動・シード投入・試験・データの後片付けまでを含む。任意のタイミングで実行するものであり、コミット前の必須チェックではない。
allowed-tools: Bash
disable-model-invocation: false
---

# 負荷試験の実行手順

**これは任意実行の手順である。** コミット前に必須の品質チェック
（[quality-check](../quality-check/SKILL.md)）とは独立していて、
負荷試験が緑でないとコミットできない、ということはない。

> **なぜ quality-check に入れないのか**
> quality-check は「Step 1〜6 をすべて緑にしてからコミットする」という
> 必須シーケンス。数分かかる負荷試験をそこに足すと、構造上
> 「毎コミット前に必須」の意味になってしまう。
> 詳細は [docs/13_performance_test.md](../../../docs/13_performance_test.md)。

設計と判断の根拠は [docs/13_performance_test.md](../../../docs/13_performance_test.md)、
実行手順の詳細は [perf/README.md](../../../perf/README.md) を参照。

---

## 前提の確認

```bash
k6 version           # v0.57 以上であること（v0.56以下はTypeScriptが動かない）
docker compose ps    # Docker Desktop が起動していること
```

k6 が入っていない場合:

```bash
winget install k6 --source winget
```

---

## Step 1: 負荷試験用DBを起動する

```bash
docker compose --profile perf up -d db-perf
```

- `--profile perf` が必要。**付けないと起動しない**（日常の開発で邪魔しないための設計）
- 開発用DB（`snstimeline` / 5432）とは別物（`snstimeline_perf` / 5433、volume も別）

## Step 2: アプリを負荷試験用DBに向けて起動する

```bash
export JWT_SECRET=$(openssl rand -base64 48)
cd backend
DB_URL=jdbc:postgresql://localhost:5433/snstimeline_perf ./mvnw spring-boot:run
```

PowerShell の場合の `JWT_SECRET` 生成は
[dev-environment](../dev-environment/SKILL.md) を参照。

- 初回は Flyway が V1〜V11 を自動適用する
- **`DB_URL` を付け忘れると開発用DBに繋がってしまう。** 試験の前後で
  TRUNCATE が走るので、付け忘れは開発データの消失を意味する。必ず確認すること

## Step 3: 試験を実行する

```bash
./perf/run-perf.sh
```

このスクリプトが以下を順に行う。

| # | 処理 |
|---|---|
| 1 | 事前チェック（DB起動 / テーブル存在 / アプリ疎通） |
| 2 | **前掃除**（前回の残骸があっても正しい状態から始める） |
| 3 | シード投入（ユーザー100人 / 投稿10,000件。約9秒） |
| 4 | k6 実行（約3分） |
| 5 | カウンタ整合の検証（TRUNCATE の**前**に行う） |
| 6 | **後掃除**（TRUNCATE） |

**データの後片付けは自動。** `trap` を張っているため、正常終了・異常終了・
閾値違反・Ctrl+C・kill のいずれでも掃除が走る。手動での掃除は不要。

### 環境変数

```bash
BASE_URL=http://localhost:8080/api/v1  ./perf/run-perf.sh   # 接続先を変える
SKIP_SEED=1                            ./perf/run-perf.sh   # シードを飛ばす
SCENARIO=perf/scenarios/timeline.ts    ./perf/run-perf.sh   # シナリオを選ぶ
```

## Step 4: 結果を読む

```
http_req_duration{name:GET /timeline}: p(95)=13.07ms  ✓ 'p(95)<1000'
http_req_failed......................: 0.00%
```

| 項目 | 見方 |
|---|---|
| **`p(95)`** | **主役。** 100回中95回がこの時間以内。目標は [docs/06_non_functional.md](../../../docs/06_non_functional.md) 1.2 |
| `http_req_waiting` | サーバーが考えていた時間。`duration` の大半を占めるなら原因はサーバー側 |
| `http_req_failed` | エラー率。0% が正常 |
| `✓` / `✗` | 閾値の合否。違反すると k6 は非0で終了する |

**閾値を満たさなかった場合**、`run-perf.sh` も非0で終了する（合否が呼び出し元に伝わる）。

## Step 5: 型検査（スクリプトを変更した場合）

```bash
cd perf && npm run typecheck
```

**k6 は TypeScript の型検査をしない**（型注釈を剥がすだけ）。
スクリプトを編集したら必ずこれを回すこと。回さないと型がただの飾りになる。

---

## 重要事項

- **負荷試験はコミット前の必須チェックではない。** 任意のタイミングで実行する
- **絶対値を「目標達成」の根拠にしない。** 負荷ツールとアプリとDBが
  同一マシンで CPU を奪い合うため、数値は相対比較（退行検知）に使う
- **開発用DBを汚さない。** Step 2 の `DB_URL` を必ず確認すること
- 測定値が前回から悪化していたら、まず `http_req_waiting` を見る。
  ここが伸びていればサーバー側（SQL / アプリロジック）が原因

### トラブルシュート

| 症状 | 対処 |
|---|---|
| `db-perf が起動していない` | Step 1 を実行する。`--profile perf` を忘れていないか |
| `テーブルが存在しない` | Step 2 を実行してアプリに Flyway を走らせる |
| `アプリに接続できない` | Step 2 のアプリが起動しているか、ポート8080が空いているか |
| `ログインに失敗した` | シードが入っていない。`SKIP_SEED=1` を外して再実行する |
| `.ts` が実行できない | k6 が v0.56 以下。`winget upgrade k6` で更新する |
| カウンタにズレがある | **D-01 のロストアップデートを疑う。** いいねの相対更新が同一トランザクション内か確認 |
