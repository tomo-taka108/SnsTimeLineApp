import { useContext } from "react";
import { ToastContext } from "./ToastProvider";

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast は ToastProvider の内側で使ってください");
  }
  return context;
}
