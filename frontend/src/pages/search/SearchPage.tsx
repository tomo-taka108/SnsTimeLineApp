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
 * 入力欄の値は別に持ち（即座に反映する）、URLへの書き戻しはデバウンス後の検索と合わせる、
 * ということはしない。URLを常に同期させ、APIを叩くタイミングだけ useUserSearch が遅らせる。
 */
export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  const q = searchParams.get("q") ?? "";
  const page = Number(searchParams.get("page") ?? "0");
  // 不正な page（?page=abc, 負値）は0として扱う。サーバーに投げても400になるだけなので画面側で正す
  const safePage = Number.isInteger(page) && page >= 0 ? page : 0;

  const { users, status, totalPages, searchedQuery, replaceUser } = useUserSearch(q, safePage);

  function handleInput(value: string) {
    // 検索語が変わったらページを0に戻す。「3ページ目を見ている状態で別の語を検索」
    // したときに page がそのままだと、結果が少ない語では空表示になってしまう。
    //
    // これを useEffect（q の変化を監視してリセット）でやってはいけない。
    // 初回マウント時にも発火するため、/search?q=x&page=2 を直接開いたときに
    // 1ページ目へ戻されてしまう（URL共有・ブックマークが壊れる）。
    // 「入力が変わった」というイベントの側で処理するのが正しい。
    if (value) {
      setSearchParams({ q: value, page: "0" }, { replace: true });
    } else {
      // 空にしたらクエリごと消す。?q= が残ると初期状態に戻ったことが分かりにくい
      setSearchParams({}, { replace: true });
    }
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
            value={q}
            onChange={(event) => handleInput(event.target.value)}
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
