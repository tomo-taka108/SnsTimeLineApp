import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AppHeader } from "../../components/AppHeader";
import { Pagination } from "../../components/Pagination";
import { StateBlock } from "../../components/StateBlock";
import { UserRow } from "../../components/UserRow";
import { useUserSearch } from "./useUserSearch";

/**
 * SC-07 ユーザー検索（docs/03_screen_design.md SC-07 / docs/05_api_design.md #20）。
 *
 * 検索語は URL の ?q= に持たせる。リロードや共有で同じ結果に戻れるようにするため。
 *
 * ただし<b>入力欄の値をURLで直接制御してはいけない</b>（日本語入力が壊れる。下記 handleChange 参照）。
 * 入力欄はローカルstateで持ち、URLへは確定した文字列だけを書き戻す。
 */
export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const q = searchParams.get("q") ?? "";
  const page = Number(searchParams.get("page") ?? "0");
  // 不正な page（?page=abc, 負値）は0として扱う。サーバーに投げても400になるだけなので画面側で正す
  const safePage = Number.isInteger(page) && page >= 0 ? page : 0;

  const { users, status, totalPages, searchedQuery, replaceUser } = useUserSearch(q, safePage);

  // 入力欄の値。URLとは別に持つ（IME対応）
  const [inputValue, setInputValue] = useState(q);
  /**
   * IMEで変換中かどうか。
   *
   * 変換中は「ｔ」「た」「たろ」のような未確定文字がReactに流れてくる。
   * これをURLに書き戻すと再レンダーで入力欄の値が差し替わり、変換が中断される。
   * 結果として「tたたrたろたろう」のように未確定文字と確定文字が混ざる。
   *
   * ref ではなく state で持つ。下の同期処理がレンダー中にこの値を読むため
   * （ref をレンダー中に読むと、値が変わっても再レンダーされず古い判定になりうる）。
   */
  const [isComposing, setIsComposing] = useState(false);

  /**
   * ブラウザの戻る/進む・URL直打ちでURLが変わったときに入力欄を追従させる。
   *
   * useEffect ではなく「レンダー中に前回値と比較して setState する」形にしている
   * （Reactが推奨する、propsから派生した状態を同期する方法）。
   * effect にすると、URLが変わるたびに「古い値で1回描画 → effectで再描画」となり
   * 入力欄が一瞬ちらつく。
   */
  const [lastSyncedQuery, setLastSyncedQuery] = useState(q);
  if (lastSyncedQuery !== q) {
    setLastSyncedQuery(q);
    // 変換中はURL側を正とせず、入力途中の値を保つ
    if (!isComposing) {
      setInputValue(q);
    }
  }

  /**
   * 入力欄からURLへ反映する。
   *
   * 検索語が変わったらページを0に戻す。「3ページ目を見ている状態で別の語を検索」
   * したときに page がそのままだと、結果が少ない語では空表示になってしまう。
   *
   * これを useEffect（q の変化を監視してリセット）でやってはいけない。
   * 初回マウント時にも発火するため、/search?q=x&page=2 を直接開いたときに
   * 1ページ目へ戻されてしまう（URL共有・ブックマークが壊れる）。
   */
  function syncToUrl(value: string) {
    if (value) {
      setSearchParams({ q: value, page: "0" }, { replace: true });
    } else {
      // 空にしたらクエリごと消す。?q= が残ると初期状態に戻ったことが分かりにくい
      setSearchParams({}, { replace: true });
    }
  }

  function handleChange(value: string, composing: boolean) {
    // 入力欄には常に即座に反映する。ここを遅らせると入力自体がもたつく
    setInputValue(value);
    // 変換確定前はURLに書き戻さない（compositionend でまとめて反映する）。
    //
    // state の isComposing ではなくイベント側の nativeEvent.isComposing を見る。
    // Chrome では compositionend の直後に change が発火することがあり、
    // そのとき setIsComposing(false) はまだ反映されていない（同一バッチ内）ため、
    // state を見ると「変換中」と誤判定して確定した文字がURLに乗らなくなる。
    if (!composing) {
      syncToUrl(value);
    }
  }

  function handleCompositionEnd(value: string) {
    setIsComposing(false);
    // 変換が確定した時点で初めてURLへ反映する。
    // compositionend と change の発火順はブラウザによって違うため、
    // ここでもイベントの値を使って明示的に同期しておく
    setInputValue(value);
    syncToUrl(value);
  }

  function handlePageChange(next: number) {
    setSearchParams({ q, page: String(next) });
    window.scrollTo({ top: 0 });
  }

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="back-bar">
          <button className="btn btn-outline btn-sm" type="button" onClick={() => navigate("/")}>
            ← 戻る
          </button>
          <div className="title">ユーザーを検索</div>
        </div>

        <div className="search-field">
          <input
            className="input"
            type="search"
            value={inputValue}
            onChange={(event) => {
              // React は onChange の nativeEvent を Event 型としており isComposing を
              // 持たないが、実体は InputEvent なので実行時には存在する
              const native = event.nativeEvent as Event & { isComposing?: boolean };
              handleChange(event.target.value, native.isComposing === true);
            }}
            onCompositionStart={() => setIsComposing(true)}
            onCompositionEnd={(event) => handleCompositionEnd(event.currentTarget.value)}
            placeholder="ユーザー名または表示名で検索"
            autoComplete="off"
            aria-label="ユーザー名または表示名で検索"
          />
          <div className="field-hint">
            ユーザー名（@taro_123）と表示名（たろう）を検索します。自己紹介とメールアドレスは検索対象外です
          </div>
        </div>

        {status === "initial" && (
          <StateBlock
            icon="🔍"
            title="ユーザーを検索しましょう"
            message="ユーザー名や表示名を入力すると、該当するユーザーが表示されます。"
          />
        )}

        {status === "loading" && (
          <>
            {[0, 1, 2].map((i) => (
              <div className="skeleton-card" key={i}>
                <div className="sk sk-avatar" />
                <div style={{ flex: 1 }}>
                  <div className="sk sk-line sk-w-30" />
                  <div className="sk sk-line sk-w-60" />
                </div>
              </div>
            ))}
          </>
        )}

        {status === "error" && (
          <div className="state-error">
            <p>検索に失敗しました</p>
            <button
              className="btn btn-outline btn-sm"
              type="button"
              onClick={() => window.location.reload()}
            >
              再試行
            </button>
          </div>
        )}

        {status === "ready" && users.length === 0 && (
          <StateBlock
            icon="🫥"
            title={`「${searchedQuery}」に一致するユーザーは見つかりませんでした`}
            message="ユーザー名の綴りを確認するか、別のキーワードで検索してみてください。"
          />
        )}

        {status === "ready" && users.length > 0 && (
          <>
            {users.map((user) => (
              <UserRow key={user.id} user={user} onChange={replaceUser} />
            ))}
            <Pagination page={safePage} totalPages={totalPages} onChange={handlePageChange} />
          </>
        )}
      </main>
    </>
  );
}
