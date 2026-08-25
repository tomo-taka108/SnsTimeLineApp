import { useEffect } from "react";
import { Route, Routes } from "react-router-dom";
import { RedirectIfAuthed } from "./auth/RedirectIfAuthed";
import { RequireAuth } from "./auth/RequireAuth";
import { useAuth } from "./auth/useAuth";
import { useToast } from "./components/useToast";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { PostDetailPage } from "./pages/PostDetailPage";
import { SignupPage } from "./pages/SignupPage";
import { TimelinePage } from "./pages/timeline/TimelinePage";

/**
 * ルート定義（docs/03_screen_design.md 2章の画面一覧に対応）。
 *
 * 今回実装するのは SC-01 / SC-02 / SC-03 / SC-04 / SC-12。
 * プロフィール・検索などは未実装のため、SC-12 に落ちる。
 */
export function App() {
  const { sessionExpired, clearSessionExpired } = useAuth();
  const { showToast } = useToast();

  // セッション切れをトーストで知らせる（docs/03_screen_design.md 8章）。
  // 画面遷移自体は RequireAuth が行う
  useEffect(() => {
    if (sessionExpired) {
      showToast("セッションの有効期限が切れました", true);
      clearSessionExpired();
    }
  }, [sessionExpired, showToast, clearSessionExpired]);

  return (
    <Routes>
      <Route
        path="/login"
        element={
          <RedirectIfAuthed>
            <LoginPage />
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/signup"
        element={
          <RedirectIfAuthed>
            <SignupPage />
          </RedirectIfAuthed>
        }
      />
      <Route
        path="/"
        element={
          <RequireAuth>
            <TimelinePage />
          </RequireAuth>
        }
      />
      <Route
        path="/posts/:postId"
        element={
          <RequireAuth>
            <PostDetailPage />
          </RequireAuth>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
