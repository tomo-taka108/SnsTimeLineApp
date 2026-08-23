/**
 * フォームのクライアント側検証（docs/05_api_design.md 8章）。
 *
 * メッセージは docs/03_screen_design.md SC-01 / SC-02 の文言、
 * およびバックエンドのDTOのメッセージと一致させている。
 * フロントで先に弾くのは即時フィードバックのためで、
 * <b>バックエンドも同じ検証を必ず行う</b>（3層で検証する方針）。
 */

/** 文字数はコードポイントで数える。絵文字を1文字として扱うため */
export function countChars(value: string): number {
  return [...value].length;
}

/** 空白のトリム。パスワードには使わない（D-27） */
export function trim(value: string): string {
  return value.trim();
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/;
/** 8文字以上、英字と数字を各1文字以上 */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,}$/;

export function validateEmail(value: string): string | undefined {
  if (!value) return "メールアドレスを入力してください";
  if (!EMAIL_PATTERN.test(value)) return "メールアドレスの形式が正しくありません";
  if (countChars(value) > 255) return "メールアドレスは255文字以内で入力してください";
  return undefined;
}

/** ログイン画面のパスワードは「必須」のみ。ポリシーを漏らさない */
export function validateLoginPassword(value: string): string | undefined {
  if (!value) return "パスワードを入力してください";
  return undefined;
}

export function validateUsername(value: string): string | undefined {
  if (!value) return "ユーザー名を入力してください";
  const length = countChars(value);
  if (length < 3 || length > 30) return "ユーザー名は3〜30文字で入力してください";
  if (!USERNAME_PATTERN.test(value)) return "ユーザー名は半角英数字とアンダースコアのみ使用できます";
  return undefined;
}

export function validateDisplayName(value: string): string | undefined {
  if (!value) return "表示名を入力してください";
  if (countChars(value) > 50) return "表示名は1〜50文字で入力してください";
  return undefined;
}

export function validateSignupPassword(value: string): string | undefined {
  if (!value) return "パスワードを入力してください";
  if (!PASSWORD_PATTERN.test(value)) return "パスワードは8文字以上で、英字と数字を含めてください";
  return undefined;
}

export function validatePasswordConfirm(password: string, confirm: string): string | undefined {
  if (!confirm) return "確認用のパスワードを入力してください";
  if (password !== confirm) return "パスワードが一致しません";
  return undefined;
}
