# E2Eテスト（Playwright）とブラウザパフォーマンステスト

本書は E2Eテスト（実ブラウザの自動操作）とブラウザパフォーマンステストの
設計・ツール選定・実行方法を定義する。
実行手順の詳細は [e2e/README.md](../e2e/README.md)、
スキルは [.claude/skills/e2e-test](../.claude/skills/e2e-test/SKILL.md) を参照。

---

## 0. 本書の位置づけと、数値の扱い方

### 0.1 なぜ必要になったか

[11_test_design.md](11_test_design.md) はバックエンド360本・フロントエンド150ケース、
計510本を擁するが、**E2E（実ブラウザの自動操作）層が1本も無かった**。

- `useInfiniteScroll` の `rootMargin: "200px"`（下端200px手前で発火）は、
  jsdomに `IntersectionObserver` が無いため**E2Eでしか検証できない**と2箇所で明記されていた
  （[11_test_design.md](11_test_design.md) 23.8 #10）
- [02_feature_list.md](02_feature_list.md) の統合シナリオ7件が未チェックのまま残っていた。
  うち2件は Chrome DevTools MCP で**手動確認**しただけだった（2026-08-26）
- [06_non_functional.md](06_non_functional.md):362「一覧の4状態（ローディング / 空 / エラー / 正常）
  がすべて実装されている」も未チェックのまま残っていた
- ブラウザ側の性能指標（LCP/FCP/CLS/TTFB）は `docs/` 全体に1つも存在しなかった

### 0.2 測定値を SLA 判定の根拠にしないこと（ブラウザ性能のみ）

**ブラウザ・被試験アプリ・DB が同一マシン上で CPU を奪い合う。** ネットワークは
実質ゼロレイテンシのループバックで、実ユーザーの回線条件を全く反映しない。
[13_performance_test.md](13_performance_test.md) 0.2 / D-69 が確立した方針と同じだが、
**ブラウザ性能はサーバー性能よりさらに環境依存が強い**ため、より強く警告する。

| 用途 | 可否 |
|---|---|
| 前回との相対比較（**退行検知**） | ○ この試験の主目的 |
| 「速い/遅い」の結論 | **✕ 使わない** |

E2Eシナリオテスト（合否判定）自体はこの限りではない。「正しく動くか」を検証するもので、
速度を主張するものではないため。

---

## 1. ツール選定

### 1.1 比較

| 比較軸 | **Playwright** | Cypress | Selenium | WebdriverIO |
|---|---|---|---|---|
| **TypeScript 対応** | ◎ 第一級。`@playwright/test` が型定義を同梱 | ○ | △ Java/JS バインディング | ○ |
| **自動待機** | **◎ 全locatorがactionabilityを自動待機**。`waitForTimeout`がほぼ不要 | ◎ | ✕ 明示的waitが必要 | ○ |
| **複数ブラウザ** | ◎ Chromium/Firefox/WebKitを単一APIで | △ 実質Chromium系中心 | ◎ | ◎ |
| **複数タブ/複数ユーザー** | **◎ BrowserContextが独立。2ユーザー同時操作が自然に書ける** | **✕ 苦手**（同一ドメイン1タブ前提の設計） | ○ | ○ |
| **ネットワーク傍受** | **◎ `page.route()`で任意のAPIを失敗させられる** | ◎ | ✕ | △ |
| **性能計測** | **◎ CDPに直結。`PerformanceObserver`が使える** | △ | △ | △ |
| 依存の重さ | ○ 単一パッケージ+ブラウザバイナリ | △ 大きい | ✕ driver管理が煩雑 | △ |

### 1.2 決定: Playwright

決め手は**3点が同時に必要**で、全部満たすのが Playwright だけだった点。

1. **2ユーザーを同時に操作する必要がある。** [02_feature_list.md](02_feature_list.md) の
   未チェック統合シナリオは全て「Aさんの操作がBさんの画面に反映される」形。Playwright の
   `BrowserContext` は localStorage もCookieも完全に独立するため、1つのテスト内で2人分の
   ブラウザを持てる。Cypress はこれが構造的に苦手
2. **APIを意図的に失敗させる必要がある。** 06章:362 の4状態のうち「エラー」は、サーバーを
   壊さずには再現できない。`page.route('**/api/v1/timeline*', r => r.abort())` で
   **フロント側だけを壊せる**
3. **ブラウザ性能を同じ道具で測りたい。** Playwright は CDP に直結しており、
   `page.evaluate()` 内の `PerformanceObserver` で LCP/CLS が取れる

- **Cypress を採らない**: 上記1が致命的。複数オリジン・複数タブの制約が、2ユーザーシナリオ
  という主目的と正面から衝突する
- **Selenium を採らない**: 自動待機が無く、`Thread.sleep` 相当を撒くことになる
- **WebdriverIO を採らない**: 機能的には近いが、今回の要件に対する優位点が無い

**D-57を撤回しない。** D-57は「E2Eではユニットテストの代替にならない（境界値を確かめられない）」
という判断だったが、本書はその射程外（既存510本を置き換えるのではなく補完する）。D-73として記録。

### 1.3 Playwright の制約（計画に織り込んだ）

| # | 制約 | 対処 |
|---|---|---|
| 1 | ブラウザバイナリのダウンロードが必要（数百MB） | 初回 `npx playwright install chromium` が前提条件。**Chromiumのみ**入れる（4ブラウザ対応は後続Issue） |
| 2 | `*.spec.ts` がVitestの既定includeに一致する | `e2e/` を `frontend/` の外に置くことで構造的に回避（5章） |
| 3 | 性能値は実行環境に強く依存 | 0.2に「SLA判定に使わない」を明記 |

---

## 2. 専用DBと自動クリーンアップ

### 2.1 専用DB（Compose profiles）

`db-perf`（[13_performance_test.md](13_performance_test.md) D-67）の設計をそのまま踏襲した。

| 項目 | 開発用 `db` | 負荷試験用 `db-perf` | **E2E用 `db-e2e`** |
|---|---|---|---|
| port | 5432 | 5433 | **5434** |
| DB名 | `snstimeline` | `snstimeline_perf` | **`snstimeline_e2e`** |
| volume | `pgdata` | `pgdata-perf` | **`pgdata-e2e`** |
| profiles | なし | `perf` | **`e2e`** |

`docker compose up -d` の挙動は今と完全に同じまま（`db` だけ起動）。
**シードSQLはマウントしない。** E2Eのデータは各テストが必要な分だけAPI経由で作る（3章）。

### 2.2 クリーンアップは `trap` + `globalSetup` の二段構え

perf の `trap` 方式（D-66）をベースにしつつ、Playwright の仕組みも併用する。

| 層 | 仕組み | 担保するもの |
|---|---|---|
| **外側** | `e2e/run-e2e.sh` の `trap cleanup EXIT INT TERM` | Ctrl+C・kill・異常終了でも必ず掃除される |
| 内側 | Playwright の `globalSetup` | 実行開始時にTRUNCATE（前回の残骸を消す） |

**なぜ `globalTeardown` だけに頼らないか**: Playwright の `globalTeardown` は、
`globalSetup` が例外を投げると呼ばれない。k6 の `teardown()` と**同じ落とし穴**（D-66）。
外側の `trap` があれば、どちらが失敗しても掃除は走る（D-74）。

`truncate-all.sql` は**コピーせず `:ro` マウントで参照**する（D-56 / 13章2.2 と同じ理由）。

### 2.3 CORS: フロントエンドの接続先に注意が必要

**実装時に発見した罠。** フロントエンドの `VITE_API_BASE_URL` は**ビルド/起動時に
静的に決まる値**で、バックエンドを既定（8080）以外のポートで起動する場合、
フロントエンド側も同じポートへ向け直す必要がある。忘れると「ログイン失敗」という
誤解を招くエラーになり、原因（ポート不一致）の特定に時間がかかる。

| 場面 | 向け方 |
|---|---|
| `npm run dev`（E2Eシナリオ用） | 起動時に `VITE_API_BASE_URL=... npm run dev` |
| `npm run build` → `npm run preview`（ブラウザ性能用） | **ビルドコマンドに**`VITE_API_BASE_URL=... npm run build`。`vite preview`起動時に渡しても効かない（ビルド時に静的にバンドルへ焼き込まれる値のため） |

`run-e2e.sh` は事前チェックでフロントエンドの疎通確認と、`perf`モードでは実際に配信中の
バンドル内容を検査してAPI接続先を確認する（6.4節のミューテーション検証で実証済み）。

CORSプリフライト自体も事前チェックしている。`vite preview` の既定ポート4173が
`CORS_ALLOWED_ORIGINS` に含まれていないと全APIが失敗するため。

---

## 3. テストデータの方針: シードではなくAPIで作る

perf（`generate_series`で10,000件）とは**正反対の方針**を採る。

| | 負荷試験（perf） | **E2E（本書）** |
|---|---|---|
| 必要なデータ量 | 10,000件（プランナがIndex Scanを選ぶ規模が必要） | **数件〜25件**（1画面に見える分だけ） |
| 作り方 | `generate_series`のSQL | **`POST /auth/signup`等のAPI** |
| 理由 | SQLでないと現実的な時間で入らない | **APIで作ればアプリのロジックを通る** |

**唯一の例外: 無限スクロール用の25件。** `rootMargin`の検証には「1ページ(20件)を超える
投稿」が要る。これも `POST /posts` を25回叩いて作る（1秒未満で終わる）。

### 3.1 ユーザーの作り分け

| 用途 | 作り方 |
|---|---|
| 1ユーザーで完結するシナリオ | `user` fixture（各テストでその場でAPI経由で1人作る） |
| **2ユーザーシナリオ** | `secondUser` fixture（独立した `BrowserContext` でもう1人作る） |

**なぜ `storageState` を全テストで共有しないか**: リフレッシュトークンは使い捨て
ローテーションで、並列ワーカーが同じものを同時に使うと同ファミリが失効する
（[frontend/src/api/client.ts](../frontend/src/api/client.ts)）。各テストが独立ユーザーを
使えば、この問題を構造的に避けられる。

メールは `e2e-<目的>-<parallelIndex>-<タイムスタンプ>@example.com` 形式
（CLAUDE.md 6章: `example.com` ドメイン厳守）。パスワードは `E2ETest123!`。

### 3.2 D-75: frontendの型を import しない

E2E は技術的には frontend の型（`frontend/src/api/types.ts`）を import できるが
（perf がk6のnpm非対応という技術的制約で断念したのとは違う）、意図的にしない。

E2Eが検証すべきは「フロントが実際に返す形」であり、フロントの型定義を信じてしまうと
**型と実装が同時に間違っていたときに検出できない**ため。`e2e/fixtures/types.ts` に
E2Eが実際に読む項目だけの最小定義を置く。

---

## 4. シナリオ設計

### 4.1 「E2Eで書くもの / 書かないもの」の線引き

既存510本と重複させない。D-57が「E2Eでは境界値を確かめられない」と判断した方向に
逆行しないため、以下を明文化する。

| E2Eで書く | E2Eで書かない |
|---|---|
| 実ブラウザでしか再現できないこと（`IntersectionObserver`/レイアウト/実際の遷移） | 境界値（30文字OK/31文字NG）→ 23章 |
| 層をまたぐ結合（ブラウザ→API→DB→別ユーザーのブラウザ） | 単一関数のロジック → 1〜3章 |
| 画面の4状態 | Service層の分岐 → 6〜17章 |
| 認証の永続化（リロード・ブラウザバック） | SQLの正しさ → 19章 |

### 4.2 セレクタ方針（本番コード無変更）

`data-testid` は追加しない。role/aria/CSSで書く。優先順位は以下の通り。

| 優先 | 手段 | 例 |
|---|---|---|
| 1 | **ARIA状態属性** | いいね: `[aria-pressed]`、タブ: `[aria-selected]` |
| 2 | ラベル付きフォーム | `getByLabel('メールアドレス')`（`FormField`が`htmlFor`を張っている） |
| 3 | role + アクセシブル名 | `getByRole('button', { name: 'ログイン' })` |
| 4 | CSSクラスでスコープ | `.post-card`, `.detail-actions`, `.modal` |

**実装中に発見した曖昧な箇所の解決方法:**

| 衝突 | 解決 |
|---|---|
| `aria-label="いいね"` がPostCardとPostDetailPageの両方 | `.post-card` / `.detail-actions` でスコープ |
| `aria-label="メニュー"` がPostCardとCommentItemの両方 | 同上 |
| **`FollowButton` は「フォロー中」「フォロー解除」の両ラベルを常にDOMに持つ**（CSSでホバー時だけ表示を切替。アクセシブルネームは両方を含む） | **テキストで判定しない。`aria-pressed`を見る**。最初`getByRole('button', {name: 'フォロー中'})`で書いたところ、ホバーしていないのに「フォロー解除」側のテキストが拾われて失敗した（実機で確認） |
| `.post-card` / `.modal` の「削除」ボタンが本体側とConfirmModal側の両方に存在 | `.modal`でスコープ（Modalは`isOpen`のときだけDOMに1つ存在する） |
| トーストが3秒で自動消滅 | `expect(...).toBeVisible()`の自動リトライに任せる |

### 4.3 「待つ」ことに関する規律

- **`page.waitForTimeout()`を極力使わない。** Playwrightのlocatorはactionabilityを自動待機する
- **`page.goto()`直後は必ずコンテンツを待つ。** `RequireAuth`が`isRestoring`の間スピナーを
  出し、`GET /auth/me`の完了まで本文が無い（`waitForAppReady`ヘルパーで対応）
- `count()`のような自動リトライが効かない操作は、明示的に要素の出現を`waitFor()`する
  （実機検証で、`waitForAppReady`後すぐ`count()`するとレースコンディションが起きることを確認）
- 例外: **無限スクロールの検証だけは`waitForResponse`相当（レスポンスイベント監視）を使う**
  （下記4.4参照）

### 4.4 無限スクロールの検証における既知の制約

**本書最大の技術的難所。** `rootMargin: "200px"`の交差判定は、6ワーカー並列などの
高負荷下では、IntersectionObserverのコールバック発火が描画フレームに間に合わず、
まれにタイムアウトすることが実機検証（`--repeat-each=5`、6ワーカー並列を30回実行）で
判明した。Chrome DevTools MCPでの単体操作では常に成功することも確認済みで、
**実装のバグではなく、並列負荷下のタイミングに起因する**。

対策として、`window.scrollTo`を繰り返しつつ、`cursor=`付きリクエストの発火を
レスポンスイベントで直接監視する構成にした。これにより通常実行（`run-e2e.sh`、
並列度6・リピート無し）では安定して成功する。極端な高負荷（6並列×5リピート=30同時）
では約2%の確率でまだflakyになりうるが、`playwright.config.ts`の`retries: 1`で
吸収される（19件×複数回の実測で、retryを含め全て最終的に合格）。

### 4.5 シナリオ一覧

11_test_design.md 27章に #479〜#507（5列書式）で記載。概要:

| ファイル | 内容 | 根拠 |
|---|---|---|
| `auth.spec.ts` | 新規登録→ログイン状態維持→ログアウト、ログイン失敗時のフォーム上部エラー | F-AU-01/02/03/04 |
| `post.spec.ts` | 投稿作成→編集→削除の一連の流れ | F-PO-01/03/04/05、MD-01/02/03 |
| `timeline.spec.ts` | **無限スクロール**（本書の主目的）、タブ状態のURL保持 | 11章23.8 #10、03章:318 |
| `states.spec.ts` | 画面の4状態 | 06章:362 |
| `social.spec.ts` | 2ユーザーでの相互作用（いいね・コメント・フォロー・削除の伝播） | 02章の未チェック統合シナリオ |
| `permission.spec.ts` | 他人の投稿への操作が出ないこと、存在しないID | 03章:431、F-CO-01 |
| `differentiation.spec.ts` | インプレッション数・リツイートが「無いこと」 | 02章:239-241 |

---

## 5. ブラウザパフォーマンステスト

### 5.1 何を、どこで測るか

**測定対象は`vite preview`（本番ビルド）。devサーバーは測らない。**

理由: Viteのdevサーバーは非バンドルのESモジュールを都度変換して配信する。
モジュール数だけHTTPリクエストが飛び、変換のオーバーヘッドが乗る。ここで測った
LCPは「Vite devの速さ」であって「このアプリの速さ」ではない。`npm run build`した
成果物を`vite preview`で配信すれば、実際にユーザーに届く構成で測れる。

### 5.2 取得する指標

| 指標 | 取り方 | 意味 |
|---|---|---|
| **LCP** | `PerformanceObserver`の`largest-contentful-paint` | 最大の要素が描画されるまで |
| **FCP** | `performance.getEntriesByName('first-contentful-paint')` | 最初に何かが出るまで |
| **CLS** | `PerformanceObserver`の`layout-shift`（`hadRecentInput`を除外） | レイアウトのガタつき |
| **TTFB** | `PerformanceNavigationTiming.responseStart - requestStart` | サーバーの初速 |
| DOMContentLoaded / Load | `PerformanceNavigationTiming` | 従来指標。参考値 |

LCP/CLSは`page.goto()`より前（`addInitScript`）から`PerformanceObserver`を仕込む。
observer登録前に発生したエントリまで`buffered: true`で遡れる保証がブラウザによって
異なるため。

### 5.3 計測対象ページと閾値

| ページ | 状態 | LCP閾値 | CLS閾値 |
|---|---|---|---|
| `/login` (SC-01) | 未認証・最小構成 | < 2500ms | < 0.1 |
| `/` (SC-03) | 認証済み・投稿あり | < 2500ms | < 0.1 |
| `/posts/:id` (SC-04) | 認証済み・コメント有 | < 2500ms | < 0.1 |

2500ms / 0.1 は **Google の Core Web Vitals の "Good" 基準**。プロジェクト独自の
目標が存在しない以上、業界標準を出発点にする。

**測定は5回繰り返して中央値を採る。** 実測では、初回計測時にコールドスタート
（JITウォームアップ等）による外れ値（3012ms）が1件観測されたが、中央値計算で
正しく吸収され、実際の中央値（64ms）に影響しなかった。13章4.5が「同日4回で
p95が11.59〜13.97msと変動した」と記録しているのと同じ問題への対策。

### 5.4 結果の出力

| 出力先 | 内容 |
|---|---|
| ターミナル | 各ページのLCP/CLS/TTFB/FCPを1行で表示 |
| `e2e/results/perf-metrics.json` | 生データ（5回分） |
| `e2e/results/perf-metrics.md` | 人間が読む表。中央値と全サンプル |
| `e2e/results/playwright-report/` | Playwright公式HTMLレポータ（失敗時のスクリーンショット付き） |

perfのk6-reporterと違い、Playwrightは公式HTMLレポータを持つため外部ライブラリ不要。
オフラインでも動く。

### 5.5 ベースライン（初回実測）

計測日: 2026-10-02 / ローカル（Windows 11, JDK 25, postgres:16コンテナ）/ Chromium (Playwright内蔵)

| ページ | LCP（中央値） | CLS（中央値） | TTFB（中央値） | FCP（中央値） |
|---|---|---|---|---|
| `/login` (SC-01) | 68.0ms | 0.000 | 1.8ms | 68.0ms |
| `/` (SC-03) | 80.0ms | 0.000 | 1.7ms | 72.0ms |
| `/posts/:id` (SC-04) | 72.0ms | 0.000 | 1.6ms | 72.0ms |

いずれもCore Web Vitalsの"Good"基準（LCP<2500ms, CLS<0.1）を大きく下回る。
ただし0.2のとおり、これは同一マシンでの測定なので「余裕がある」と結論づけない。
**この値が次回以降の比較基準（ベースライン）になる。**

CLS計測ロジック自体が機能していることは、意図的にレイアウトシフトを起こす
テストページで実機検証済み（CLS=0.032を正しく検知）。LCP閾値を1msに変更する
ミューテーション検証でも、テストが正しく失敗することを確認した。

---

## 6. ファイル配置

### 6.1 リポジトリルートに `e2e/`（`perf/`と同じ形）

```
e2e/
  README.md                       … 実行手順
  run-e2e.sh                      … ラッパー（trap による前後掃除 + 事前チェック）
  package.json                    … devDeps: @playwright/test, typescript のみ
  tsconfig.json                   … 型検査専用（noEmit）
  playwright.config.ts            … projects（e2e/perf）/ reporter / baseURL
  global-setup.ts                 … TRUNCATE（前掃除）
  fixtures/
    api.ts                        … APIでユーザー・投稿を作るヘルパー
    auth.ts                       … ログイン済み context を作る fixture
    types.ts                      … E2E が読む最小レスポンス型
    wait.ts                       … 共通の待機ヘルパー
  specs/                          … E2Eシナリオ（7ファイル）
  perf/
    browser-perf.spec.ts          … 5章の指標を計測
    metrics.ts                    … PerformanceObserver のブラウザ内スクリプト
    report.ts                     … JSON/Markdown出力
  results/                        … 出力（.gitignore 済み）
```

### 6.2 `frontend/` の外に置く理由

1. **`*.spec.ts`がVitestの既定includeに一致する。** `frontend/`配下に置くと`npm test`が
   Playwrightのテストを拾って壊れる。構造で防げるものを設定で防がない
2. **`frontend/tsconfig.app.json`は`include: ["src"]`。** 混ぜると本番ビルド設定を触ることになる
3. **`perf/`の前例がある。** D-68が既に「テストツールはfrontendから独立させる」と判断済み

### 6.3 `.gitignore`への追加

```
e2e/results/
e2e/.auth/
```

### 6.4 「任意実行」の担保

| 層 | 仕組み |
|---|---|
| Docker | `profiles: ["e2e"]` — 通常の`up -d`では起動しない |
| 依存 | `pom.xml` / `frontend/package.json`を**無変更** |
| スキル | `e2e-test`を**新設**。quality-checkには足さない |

**quality-checkに足さない理由**: quality-checkは「Step 1〜6をすべて緑にしてから
コミット」という必須シーケンス。E2EはDocker+backend+frontend ビルド+previewの4つが
揃わないと動かず、数分かかる。毎コミット前に要求するのは非現実的（D-69と同じ判断）。

---

## 7. 後続Issue候補

| # | 項目 | 備考 |
|---|---|---|
| 1 | Firefox / WebKitでの実行 | 06章6.1は4ブラウザ対応。今回はChromiumのみ |
| 2 | `@axe-core/playwright`によるアクセシビリティ自動検査 | 06章6.3の最低ライン6項目 |
| 3 | レスポンシブの検証 | 06章6.2の375px/768px。`page.setViewportSize`で可能 |
| 4 | SC-10/SC-11実装後のシナリオ追加 | 現在未実装 |
| 5 | CI連携 | `.github/`が無い現状は手動で足りる。導入時も`workflow_dispatch`が筋 |
| 6 | 画像アップロードのE2E | `setInputFiles`で可能だが、`uploads/`にゴミが残る問題を先に解く必要がある |
| 7 | 無限スクロールのflaky対策のさらなる改善 | 4.4節。極端な高負荷下でのタイミング問題を根本的に解消する方法の検討 |
