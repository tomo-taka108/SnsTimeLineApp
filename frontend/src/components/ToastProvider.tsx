import { createContext, useCallback, useMemo, useRef, useState, type ReactNode } from "react";

/**
 * トースト（docs/03_screen_design.md 5章「画面下部、3秒で自動消滅」）。
 *
 * 403 / 500 / 通信エラー / セッション切れの通知に使う。
 * フォームのバリデーションエラーはトーストにしない（フィールド直下に出す）。
 */

const DURATION_MS = 3000;

type Toast = {
  id: number;
  message: string;
  isError: boolean;
};

type ToastContextValue = {
  showToast: (message: string, isError?: boolean) => void;
};

// eslint-disable-next-line react-refresh/only-export-components
export const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(0);

  const showToast = useCallback((message: string, isError = false) => {
    const id = nextId.current++;
    setToasts((prev) => [...prev, { id, message, isError }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, DURATION_MS);
  }, []);

  const value = useMemo<ToastContextValue>(() => ({ showToast }), [showToast]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {toasts.length > 0 && (
        <div className="toast-area" aria-live="polite">
          {toasts.map((toast) => (
            <div key={toast.id} className={toast.isError ? "toast is-error" : "toast"}>
              {toast.message}
            </div>
          ))}
        </div>
      )}
    </ToastContext.Provider>
  );
}
