import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import { setSessionExpiredHandler } from "../api/client";
import { clearTokens, getAccessToken, saveTokens } from "../api/tokenStorage";
import type { AuthResponse, LoginPayload, SignupPayload, UserSummary } from "../api/types";

/**
 * 認証状態（docs/07_architecture.md 7章）。
 *
 * 起動時に GET /auth/me を呼んで状態を復元する（F-AU-04）。
 * リロードしてもログイン状態が維持されるのはこの仕組みによる。
 */

type AuthContextValue = {
  user: UserSummary | null;
  /** 起動直後の復元処理が終わるまで true。この間はルート判定を保留する */
  isRestoring: boolean;
  /** セッション切れのときに立つ。トースト表示用 */
  sessionExpired: boolean;
  clearSessionExpired: () => void;
  login: (payload: LoginPayload) => Promise<void>;
  signup: (payload: SignupPayload) => Promise<void>;
  logout: () => Promise<void>;
};

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [isRestoring, setIsRestoring] = useState(true);
  const [sessionExpired, setSessionExpired] = useState(false);

  // APIラッパーがセッション終了を検知したときに呼ばれる。
  // 「トークンを消す」のはラッパー側で済んでいるので、ここでは画面の状態だけ落とす。
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null);
      setSessionExpired(true);
    });
    return () => setSessionExpiredHandler(null);
  }, []);

  // 起動時の復元。トークンがあれば GET /auth/me で有効性を確かめる
  useEffect(() => {
    let cancelled = false;

    async function restore() {
      if (!getAccessToken()) {
        // トークンが無ければ未ログイン。APIは呼ばない
        if (!cancelled) setIsRestoring(false);
        return;
      }

      try {
        const me = await authApi.fetchMe();
        if (!cancelled) setUser(me);
      } catch {
        // 401ならAPIラッパーが再発行を試み、それも失敗していればトークンは消えている。
        // ここでは未ログイン扱いにするだけでよい。
        // 起動時のセッション切れでトーストは出さない（初回訪問と区別がつかないため）
        if (!cancelled) {
          setUser(null);
          setSessionExpired(false);
        }
      } finally {
        if (!cancelled) setIsRestoring(false);
      }
    }

    void restore();
    return () => {
      cancelled = true;
    };
  }, []);

  /** ログイン・新規登録に共通の後処理 */
  const applyAuthResponse = useCallback((res: AuthResponse) => {
    saveTokens(res.accessToken, res.refreshToken);
    setUser(res.user);
    setSessionExpired(false);
  }, []);

  const login = useCallback(
    async (payload: LoginPayload) => {
      applyAuthResponse(await authApi.login(payload));
    },
    [applyAuthResponse],
  );

  const signup = useCallback(
    async (payload: SignupPayload) => {
      // 登録APIがトークンを返すため、そのままログイン状態になる（F-AU-01）
      applyAuthResponse(await authApi.signup(payload));
    },
    [applyAuthResponse],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // 失敗しても続行する。サーバーに届かなくても、
      // 手元のトークンを消してログアウト状態にすることを優先する
    } finally {
      clearTokens();
      setUser(null);
      setSessionExpired(false);
    }
  }, []);

  const clearSessionExpired = useCallback(() => setSessionExpired(false), []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, isRestoring, sessionExpired, clearSessionExpired, login, signup, logout }),
    [user, isRestoring, sessionExpired, clearSessionExpired, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
