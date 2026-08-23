import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./useAuth";

/**
 * ログイン・新規登録画面を包む。
 *
 * 既にログイン済みなら SC-03 へ送る
 * （docs/03_screen_design.md SC-01「ログイン済みの場合は SC-03 へリダイレクト」）。
 */
export function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { user, isRestoring } = useAuth();

  if (isRestoring) {
    return (
      <div className="page-center">
        <span className="spinner" />
      </div>
    );
  }

  if (user) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
