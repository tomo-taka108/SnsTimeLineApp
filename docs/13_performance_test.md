# パフォーマンステスト（負荷試験）

本書は負荷試験の設計・ツール選定・実行方法を定義する。
実行手順の詳細は [perf/README.md](../perf/README.md)、
スキルは [.claude/skills/perf-test](../.claude/skills/perf-test/SKILL.md) を参照。

---

## 0. 本書の位置づけと、数値の扱い方

### 0.1 なぜ必要になったか

[06_non_functional.md](06_non_functional.md) 1.2 に応答時間の目標が定義されているが、
**それを実測する手段が存在しなかった**。

- 1.4「性能の検証方法」は `EXPLAIN ANALYZE` の単発実行のみで、単一SQLの実行計画しか見ていない
- その結果、**7章の性能チェックリスト3項目が未チェックのまま**だった
- [11_test_design.md](11_test_design.md) はバックエンド360本・フロント150ケースを擁するが、**性能テスト層が1本も無い**

さらに、実測で裏付けられていない性能主張が [09_decision_log.md](09_decision_log.md) に残っている（D-31 / D-59）。

### 0.2 測定値を SLA 判定の根拠にしないこと

**負荷ツール（k6）と被試験アプリと DB が同一マシン上で CPU を奪い合う。**
得られる絶対値は「このマシンでこの構成のとき」の値にすぎない。

| 用途 | 可否 |
|---|---|
| 前回との相対比較（**退行検知**） | ○ この試験の主目的 |
| ボトルネックの特定 | ○ |
| 改善の前後比較 | ○ |
| **「1秒以内を達成した」と主張する根拠** | **✕ 使わない** |

AWS 構築（D-21）後に `BASE_URL` を差し替えて再測すれば、そのときの数値が実力値になる。
シナリオは**一切変更せずに**向け先だけ変えられる設計にしてある。

---

## 1. ツール選定

### 1.1 比較

| 比較軸 | **k6** | Gatling JS | Artillery | JMeter |
|---|---|---|---|---|
| **TypeScript 対応** | **◎ v0.57+ で既定ON。`k6 run x.ts` が直接動く** | ○ 公式SDK。実体は GraalVM→JVM 変換 | △ TS は processor 止まり。**正本は YAML** | ✕ 非対応 |
| **TS が「主役」か** | **◎ シナリオ全体を TS で書く** | ◎ | △ YAML が主役 | ✕ |
| 学習コスト | ◎ 単一バイナリ | △ GraalVM/Coursier/JVM の層が増える | ○ | ✕ GUI + 巨大XML |
| **既存スタック親和性** | **◎ pom.xml も frontend/package.json も無変更** | △ **別JVM が自動DLされる**。JDK 25 と二重管理 | ○ Node だが別 package.json 必須 | ✕ |
| **任意実行 / 依存グラフ非汚染** | **◎** | △ | ○ | △ |
| 閾値による合否自動判定 | **◎ 違反で非0 exit** | ◎ | △ | △ |
| 情報量 | ◎ 最多 | ○ | ○ | ◎（古い情報が多い） |

### 1.2 決定: k6（TypeScript）

決め手は **「TS が主役」と「依存グラフを汚さない」を同時に満たすのが k6 だけ**だった点。

- **Gatling JS を採らない**: CLI が `~/.gatling` に **GraalVM と Coursier を自動ダウンロード**する。プロジェクトの JDK 25 とは別の JVM が暗黙に増え、「どの Java で動いているか」が不透明になる。`gatling-maven-plugin` で Java DSL を使う道もあるが、**それでは TS でなくなる**
- **Artillery を採らない**: Node ネイティブという最大の利点（frontend の型再利用）は、**k6 でも npm 経由の型再利用が不可能である以上そもそも活かせない**（1.3 の制約2）。一方で「正本が YAML」という欠点は残る。主目的がカーソルページネーションという**手続き的シナリオ**なので TS で素直に書ける k6 が優位
- **JMeter を採らない**: TS 非対応。`.jmx` は差分レビューが不可能な巨大XML

### 1.3 k6 の制約（公式ドキュメントで確認済み）

| # | 制約 | 出典の記述 | 対処 |
|---|---|---|---|
| 1 | **型検査はしない** | TypeScript support is partial as it strips the type information but doesn't provide type safety. | `perf/` で `tsc --noEmit` を自前で回す（5.2） |
| 2 | **npm 解決をしない** | `require()` は built-in k6 modules / local filesystem / HTTP(S) のみ対応 | frontend の型は import 不可。`perf/lib/types.ts` に最小定義 |
| 3 | v0.57 が境界 | Starting on k6 v0.57, TypeScript support is enabled by default | **v0.57 以上を必須要件とする** |

出典: [JavaScript and TypeScript compatibility mode | Grafana k6](https://grafana.com/docs/k6/latest/using-k6/javascript-typescript-compatibility-mode/)

---

## 2. 専用DBと自動クリーンアップ

### 2.1 専用DB（Compose profiles）

| 項目 | 開発用 `db` | 負荷試験用 `db-perf` |
|---|---|---|
| port | 5432 | **5433** |
| DB名 | `snstimeline` | **`snstimeline_perf`** |
| volume | `pgdata` | **`pgdata-perf`** |
| profiles | なし（既定で起動） | **`perf`** |

`profiles: ["perf"]` により、通常の `docker compose up -d` では**起動しない**。
負荷試験のときだけ `docker compose --profile perf up -d db-perf` で足す。
**日常の開発体験を一切変えないための措置。**

アプリの向け先は `DB_URL` 環境変数で差し替える。
`application.yml` は既にプレースホルダ化されているため、**設定ファイルは無変更**。
Spring プロファイル（`application-perf.yml`）は作らない。

### 2.2 クリーンアップは k6 の teardown ではなく trap で行う

**k6 の `teardown()` に掃除を任せてはいけない。**

| # | 理由 | 根拠 |
|---|---|---|
| 1 | `setup()` が異常終了すると `teardown()` は呼ばれない | [k6 公式](https://grafana.com/docs/k6/latest/using-k6/test-lifecycle/): If the Setup function ends abnormally, the teardown() function isn't called. |
| 2 | 2回目の Ctrl+C は `ExternalAbort` で即死する | [grafana/k6#2804](https://github.com/grafana/k6/issues/2804) |
| 3 | **そもそも k6 は DB に接続できない** | TRUNCATE を発行する手段がない |

したがって掃除は **k6 の外側、`perf/run-perf.sh` の `trap`** で行う。

```sh
trap cleanup EXIT INT TERM
cleanup               # ① 前掃除（前回の残骸があっても正しい状態から始める）
seed                  # ② シード投入
k6 run ... || rc=$?   # ③ 試験
verify-counters       # ④ カウンタ整合の検証（TRUNCATE の前）
exit $rc              # ⑤ EXIT trap で後掃除 → 合否を返す
```

| 設計点 | 理由 |
|---|---|
| `EXIT` を含める | `set -e` の異常終了・閾値違反の非0 exit・正常終了のすべてを捕捉する |
| `INT` / `TERM` | Ctrl+C と kill |
| 冒頭でも `cleanup` を呼ぶ | 前回が強制終了してデータが残っていても正しく始まる。TRUNCATE は空テーブルでも成功するので無条件で安全 |
| **トラップ内で `exit $exit_code`** | `return` だと EXIT トラップの戻り値が終了ステータスを**上書きしてしまい、閾値違反が呼び出し元に伝わらない**（実装時に実際に踏んだ） |
| 整合検証は TRUNCATE の**前** | 掃除してから検証しても全テーブルが空で意味がない |

後片付けの SQL は [backend/src/test/resources/sql/truncate-all.sql](../backend/src/test/resources/sql/truncate-all.sql) を
`:ro` マウントで**参照**する。コピーしないのは、テーブル追加時に片方が腐るのを防ぐため。
あのファイルが持つ「`flyway_schema_history` は消さない」という正しさも引き継げる。

---

## 3. シードデータ

### 3.1 方式: generate_series の SQL を psql で流す

Java の main や Maven プラグインを使わない理由:
(1) 1件ずつ INSERT すると JDBC ラウンドトリップが支配的になる、
(2) `pom.xml` にプラグインを増やすと依存グラフを汚す。

[TestFixtures.java](../backend/src/test/java/com/example/snstimeline/support/TestFixtures.java) は
**コードとしては流用せず**、列名・カウンタ更新規則・`example.com` 規約の
「**仕様の参照元**」として使った。

### 3.2 件数（06_non_functional.md 1.1 に準拠）

| 項目 | 想定 | 実測 |
|---|---|---|
| ユーザー数 | 100人 | 100 ✅ |
| 投稿数 | 10,000件 | 10,000 ✅ |
| 1ユーザーのフォロー数 | 最大50人 | 最大50 ✅ |
| 1投稿のコメント数 | 平均5件、最大100件 | 平均5.0 / 最大100 ✅ |
| 1投稿のいいね数 | 平均10件、最大100件 | 平均10.0 / 最大100 ✅ |

投入時間: **約9秒**。`setseed(0.42)` により**何度実行しても同一データ**になる
（これがないと「前回と比べて遅くなった」の比較が成立しない）。

### 3.3 created_at の分布が設計の要

| 分布 | 件数 | 検証する対象 |
|---|---|---|
| ① 過去90日にばらけた値 | 約9,900件 | `idx_posts_timeline` が使われること |
| ② **同一 `created_at` の塊**（20件×4箇所） | 80件 | **D-33 の退行検知** |
| ③ 直近の投稿 | 18件 | `new-count` API 用 |
| ④ 論理削除済み | 50件 | `deleted_at IS NULL` の絞り込み（D-02） |

**②が重要。** カーソルは `(created_at, id)` の行値比較でページを繰るため、
同一時刻の投稿が無いと「タイブレーカーが壊れていても気づけない」。
D-33 は実際にこのバグ（秒精度で同一秒内の投稿を取りこぼす）を踏んでいる。

> **罠**: `posts.created_at` の `DEFAULT now()` は**トランザクション開始時刻**を返す。
> 単一の `INSERT ... SELECT` で入れると**全件が同一時刻になる**ため、①では
> `created_at` を必ず明示指定する。検証項目として `count(DISTINCT created_at)` が
> 1 でないことを確認している。

### 3.4 パスワード（BCrypt ハッシュの共有）

負荷試験は実際に `POST /auth/login` を叩くため、本物の BCrypt ハッシュが要る。
だが100人分を SQL で生成することはできない。

**全ユーザーで同一ハッシュを共有する。** BCrypt は同じパスワードでもソルトが違えば
ハッシュが変わるが、検証は「そのハッシュにそのパスワードが合うか」なので、
ハッシュを共有しても全員が同じパスワードでログインできる。生成1回・投入0秒。

```
メール   : perfuser0001@example.com 〜 perfuser0100@example.com
パスワード: PerfTest123
```

**この値は負荷試験専用DBにしか存在しない。** 開発用DBにも本番にも入らないため、
リテラル埋め込みを許容する（CLAUDE.md 6章の趣旨は満たしている。
`example.com` ドメイン、実在の個人名なし）。

---

## 4. シナリオと合否基準

### 4.1 「1秒以内」を p95 と解釈する

[06_non_functional.md](06_non_functional.md) 1.2 は統計量を書いていない。**p95 に落とす。**

根拠: [12_logging_and_operations.md](12_logging_and_operations.md) 7章が
「**p95レイテンシ** … 06 の 1.2 の応答時間目標と対応させる」と書いており、
**リポジトリ内で唯一の解釈がこれ**。新たな解釈を持ち込むより既存記述に揃える。

max を基準にするとローカル同一マシンでの GC 1回で落ち、**信号ではなくノイズを測る**ことになる。
p99 も記録するが値は緩める（テール劣化の傾向を見るため）。

### 4.2 負荷パラメータ

| 項目 | 値 | 根拠 |
|---|---|---|
| VU | 10 → 30（ramp-up） | 想定ユーザー100人のうち同時アクティブはその一部。ローカルではこれ以上だと**測定側がボトルネック**になる |
| stages | 30s→10, 60s→30, 60s維持, 30s→0 | 立ち上がり直後の JIT ウォームアップを p95 に混ぜない |
| 試験時間 | **約3分** | 気軽に回せる長さ。10分かかると回されなくなる |
| エラー率 | `rate<0.01` | レート制限未実装（D-17）なので 429 で弾かれる心配はない |
| RPS | 固定しない | 目的が「目標応答時間を満たすか」なので、到達RPSは**結果として記録する** |

### 4.3 Phase 1: タイムライン取得

`GET /timeline?tab=all&limit=20` を `nextCursor` で3ページ辿る。

| threshold | 根拠 |
|---|---|
| `p(95)<1000` | 06 1.2「タイムライン20件の取得 1秒以内」 |
| `p(99)<2000` | テール劣化の傾向を見る（緩め） |
| `http_req_failed: rate<0.01` | エラーが出ていないこと |
| `checks: rate>0.99` | D-33 の重複チェックを含む |

**なぜこれが最初か**: 最頻出かつ 06 に数値目標が明記された唯一の中心API。
これ1本で「専用DB・シード・自動掃除・thresholds」の全部品が通る。

### 4.4 結果の出力先

試験が終わると `perf/lib/report.ts` の `handleSummary()` が3種類の出力を作る。

| 出力先 | 内容 |
|---|---|
| ターミナル（stdout） | 色付きの標準サマリ。実行直後にその場で見る |
| `perf/results/summary.json` | 生データ。再集計や長期保存に使う |
| `perf/results/summary.html` | **人間が見やすいHTMLレポート**。ブラウザで開く |

HTML レポートは [benc-uk/k6-reporter](https://github.com/benc-uk/k6-reporter)（バージョン固定）を使っている。
総リクエスト数・失敗数・閾値違反数・チェック失敗数がカードで一目瞭然になり、
エンドポイントごとの応答時間分布（avg/min/med/max/p90/p95）が表で見られる。

**実行時にインターネット接続が必要**（GitHub / jsDelivr からライブラリを取得するため）。
オフラインでもテスト本体（thresholds の合否判定）は実行できるが、レポート生成だけ失敗する。
これは `handleSummary()` が試験終了**後**に呼ばれるためで、合否そのものには影響しない。

いずれも `.gitignore` 済みで**実行のたびに上書きされる**。複数回分を比較したい場合は
実行のたびに別名で保存する（例: `cp perf/results/summary.html perf/results/2026-09-30.html`）。
長期的な記録は本書 4.5「ベースライン」に書き残す。

### 4.5 ベースライン（初回実測）

計測日: 2026-09-30 / ローカル（Windows 11, JDK 25, postgres:16 コンテナ）/ k6 v2.2.0

> 実装中に同日3回計測しており、以下は`perf/results/summary.html`（HTMLレポート機能追加後の
> 最終実行）と一致する値。3回の p95 は 13.07ms → 13.97ms → **11.59ms** と数ミリ秒単位で
> 変動した（0.2 に書いたとおり、同一マシンでの測定は毎回ノイズを受けるため）。

| 指標 | 実測 | 閾値 | 判定 |
|---|---|---|---|
| `http_req_duration{name:GET /timeline}` p95 | **11.59ms** | < 1000ms | ✅ |
| `timeline_first_page_duration` p95 | 11.66ms | < 1000ms | ✅ |
| `http_req_failed` | 0.00%（0/10,498） | < 1% | ✅ |
| `checks` | 100.00%（31,492件） | > 99% | ✅ |
| スループット | 58.1 req/s | （記録のみ） | — |
| 総リクエスト数 | 10,498 | | |

**カウンタ整合**: ズレ 0件（10,498リクエスト後も D-01 の不変条件を維持）
**D-33 の重複チェック**: 全パス（同一時刻の投稿80件を含むデータでページ跨ぎの重複なし）

`EXPLAIN ANALYZE`（10,000件投入後）:

```
Limit  (cost=0.29..6.32 rows=20)
  ->  Index Scan using idx_posts_timeline on posts p
Execution Time: 0.187 ms
```

**`Seq Scan` ではなく `Index Scan`。** 06 1.4 の要求を初めて実測で確認した。

> p95 が目標の 1/86 という大きな余裕がある。ただし 0.2 のとおり、
> これは同一マシンでの測定なので「余裕がある」と結論づけないこと。
> **この 11.59ms が次回以降の比較基準（ベースライン）になる。**

### 4.6 Phase 2 以降（未実装）

| # | シナリオ | 合否判定 | 狙い |
|---|---|---|---|
| ② | ユーザー検索 `GET /users?q=` | **閾値を設けず実測値を記録** | D-50 が「Seq Scan 前提」と宣言した状態の定量化。D-17（レート制限）の候補値の根拠にもなる |
| ③ | 同一投稿への集中いいね | `p(95)<500` **＋ カウンタ整合SQLが0行** | 速度だけでなく**正しさ（D-01 のロストアップデート非発生）**を検証。シードに `[HOTSPOT]` 投稿を用意済み |
| ④ | new-count ポーリング | ①に混ぜる | **D-31 の未実測主張の裏付け** |
| ⑤ | N+1 退行検知（SQL 3回以内） | — | **k6 では判定不能**。`pg_stat_statements` が要る（後続Issue） |

---

## 5. ファイル配置

```
perf/
  README.md              … 実行手順
  run-perf.sh            … ラッパー（trap による前後掃除）
  package.json           … devDeps: typescript, @types/k6 のみ
  tsconfig.json          … 型検査専用（noEmit）
  scenarios/timeline.ts  … Phase 1
  lib/auth.ts            … login → token
  lib/types.ts           … perf 専用の最小レスポンス型
  lib/report.ts          … handleSummary()（JSON + HTML レポート出力）
  lib/external.d.ts      … URL importのアンビエント型宣言（tsc用）
  seed/*.sql             … シードデータ + カウンタ整合検証
  results/                … 実行結果（.gitignore 済み、実行のたびに上書き）
```

### 5.1 backend/src/test に置かない理由

surefire が拾う対象に入ると「毎回自動実行しない」に反する。`.ts` は Maven のビルド対象として異物。

### 5.2 perf/ を frontend から独立させる（D-58 の射程外）

D-58 は「テストコードを `src/` に置き、本番と同じ型検査・静的解析に掛ける」だが、
**その射程は frontend のユニットテストで、負荷試験スクリプトには届かない。**

| 判断 | 理由 |
|---|---|
| **独立した `package.json` / `tsconfig.json` を持つ** | `@types/k6` は frontend のビルドに一切不要。`frontend/tsconfig.app.json` は `include: ["src"]` なので混ぜるには include 拡張が必要で、**本番ビルド設定に負荷試験の型が混ざる**。D-58 が `vitest/globals` を拒否したのと同じ理由 |
| **型検査はする**（`tsc --noEmit`） | k6 は型を strip するだけで検査しない（1.3 制約1）。検査しないと型注釈がただの飾りになる |
| **oxlint の対象にはしない** | 数ファイルの手続きスクリプトで、lint 規約の二重管理は過剰 |

**型の再利用**: 1.3 の制約2により frontend の型の import は技術的に不可能。
`perf/lib/types.ts` に負荷試験が実際に読む項目だけを最小定義する。
重複だが「**フロントの型を負荷試験の都合で変更しなくて済む**」利点がある。

### 5.3 「任意実行」の担保

| 層 | 仕組み |
|---|---|
| Docker | `profiles: ["perf"]` — 通常の `up -d` では起動しない |
| 依存 | `pom.xml` / `frontend/package.json` を**無変更** |
| スキル | `perf-test` を**新設**。quality-check には足さない |

**quality-check に Step 7 として足さない理由**: あのスキルは
「Step 1〜6 をすべて緑にしてからコミット」という必須シーケンス。
そこに足すと**構造上「コミット前に必須」の意味になる**。
数分かかる試験を毎コミット前に要求するのは要求に反する。
同一ファイル内に「必須」と「任意」が混在するのも事故のもと。

---

## 6. 後続Issue候補

| # | 項目 | 備考 |
|---|---|---|
| 1 | **Actuator / Micrometer 導入** | 負荷試験中の JVM・HikariCP の観測。`/actuator/health` は [10_infrastructure.md](10_infrastructure.md) 4.1 の既知未対応。**本PRに混ぜると目的が2つになるため分離した** |
| 2 | `pg_stat_statements` による N+1 自動検証 | 06 1.3 要求1「SQL発行3回以内」。k6 では判定不能 |
| 3 | **06 7章 性能チェックリストの更新** | 4.5 の実測値が出たので ✓ を付けられる。1.1/1.2 への同時接続数・RPS・p95/p99 の追記も |
| 4 | [11_test_design.md](11_test_design.md) に性能テスト章 | 26章・#479以降。5列書式（#／技法／入力／期待／**根拠**） |
| 5 | D-17 レート制限の候補値 | Phase 2 ②の実測値から決める |
| 6 | HikariCP のチューニング | 現在は**既定10のまま**。プール枯渇を観測してから変える方針（根拠なき設定を避ける） |
| 7 | CI 連携 | `.github/` が無い現状は手動運用で足りる。導入時も `workflow_dispatch`（任意実行）が筋 |
