import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "./useAuth";

/**
 * 認証が必要な画面を包む。未ログインなら SC-01 へ送る。
 *
 * 起動直後の復元中は判定を保留する。ここで待たないと、
 * ログイン済みでもリロードのたびに一瞬ログイン画面が見えてしまう。
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, isRestoring } = useAuth();

  if (isRestoring) {
    return (
      <div className="page-center">
        <span className="spinner" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}
