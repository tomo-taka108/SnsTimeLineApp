import { useContext } from "react";
import { AuthContext } from "./AuthContext";

/**
 * 認証状態を取り出す。
 *
 * AuthProvider の外で使われたら分かるように、例外で落とす。
 * null が返ると呼び出し側で毎回チェックが必要になり、間違いに気づきにくい。
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth は AuthProvider の内側で使ってください");
  }
  return context;
}
