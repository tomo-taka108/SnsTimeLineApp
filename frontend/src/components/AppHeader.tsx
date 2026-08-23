import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/useAuth";
import { Avatar } from "./Avatar";

/**
 * 共通ヘッダー（docs/03_screen_design.md 4.1）。
 *
 * ログイン時のみ表示する。SC-01 / SC-02 では出さない。
 * 検索アイコンとプロフィール・設定は該当画面が未実装のため、今回は置かない。
 */
export function AppHeader() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  // メニューの外側をクリックしたら閉じる
  useEffect(() => {
    if (!isOpen) return;

    function handleClickOutside(event: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setIsOpen(false);
    }

    document.addEventListener("mousedown", handleClickOutside);
    document.addEventListener("keydown", handleEscape);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
      document.removeEventListener("keydown", handleEscape);
    };
  }, [isOpen]);

  if (!user) return null;

  async function handleLogout() {
    setIsLoggingOut(true);
    // logout は内部で失敗を握りつぶし、必ずトークンを消す
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <header className="app-header">
      <Link className="logo" to="/">
        <span className="logo-mark" aria-hidden="true">
          ◉
        </span>
        SnsTimeLine
      </Link>

      <div className="header-actions">
        <div className="avatar-menu" ref={menuRef}>
          <button
            className="avatar-menu-trigger"
            type="button"
            onClick={() => setIsOpen((prev) => !prev)}
            aria-haspopup="menu"
            aria-expanded={isOpen}
            aria-label="アカウントメニュー"
          >
            <Avatar user={user} size="sm" />
            <span className="caret" aria-hidden="true">
              ▼
            </span>
          </button>

          <div className={isOpen ? "dropdown is-open" : "dropdown"} role="menu">
            {/* モックでは <a> だが、ログアウトは処理を伴うので button にする */}
            <button type="button" role="menuitem" onClick={handleLogout} disabled={isLoggingOut}>
              <span aria-hidden="true">🚪</span>
              {isLoggingOut ? "ログアウト中..." : "ログアウト"}
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
