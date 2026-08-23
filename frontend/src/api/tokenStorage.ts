/**
 * トークンの保管（docs/09_decision_log.md D-07 / D-29）。
 *
 * 保管場所は localStorage。別オリジン構成で Authorization ヘッダーに載せるため、
 * Cookie ではなくこちらを採用している（CSRF対策が不要になる代わりにXSSに弱い）。
 *
 * <b>この前提として、XSS を起こさない実装を徹底する必要がある。</b>
 * - dangerouslySetInnerHTML を使わない
 * - ユーザー入力をHTMLとして解釈しない
 *
 * localStorage への読み書きはこのファイルに閉じ込める。
 * 散らばると「消し忘れ」が起きて、ログアウトしたのにトークンが残る事故になる。
 */

const ACCESS_TOKEN_KEY = "snstimeline.accessToken";
const REFRESH_TOKEN_KEY = "snstimeline.refreshToken";

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

/**
 * 2つのトークンをまとめて保存する。
 *
 * <b>リフレッシュトークンは必ず新しい値で上書きすること。</b>
 * 使い捨て（ローテーション）のため、古い値を持ち続けて再送すると
 * バックエンドに盗用とみなされ、そのログインのトークンが全て失効する。
 */
export function saveTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

/** 両方削除する。ログアウトとリフレッシュ失敗時に呼ぶ */
export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}
