import { AppHeader } from "../components/AppHeader";
import { Avatar } from "../components/Avatar";
import { useAuth } from "../auth/useAuth";

/**
 * SC-03 の暫定ページ。
 *
 * 本来はタイムライン（投稿一覧）だが、投稿機能が未実装のため、
 * <b>ログインできていることが分かる</b>最小限の表示にとどめる。
 * 投稿一覧の実装時にこの中身を差し替える。
 */
export function HomePage() {
  const { user } = useAuth();

  // RequireAuth を通っているので user は必ず存在する
  if (!user) return null;

  return (
    <>
      <AppHeader />
      <main className="app-main">
        <div className="welcome">
          <p className="welcome-badge">
            <span aria-hidden="true">✓</span> ログインしています
          </p>

          <Avatar user={user} size="lg" />
          <h1>{user.displayName}</h1>
          <p className="handle">@{user.username}</p>

          <p className="next-note">
            この画面は認証の動作確認用の仮ページです。
            <br />
            投稿一覧（タイムライン）は次回実装します。
            <br />
            右上のメニューからログアウトできます。
          </p>
        </div>
      </main>
    </>
  );
}
