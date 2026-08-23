import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { ToastProvider } from "./components/ToastProvider";
import "./styles/global.css";

const container = document.getElementById("root");
if (!container) {
  throw new Error("#root が見つかりません");
}

createRoot(container).render(
  <StrictMode>
    <BrowserRouter>
      {/* AuthProvider がトーストを出すため、ToastProvider は外側に置く */}
      <ToastProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);
