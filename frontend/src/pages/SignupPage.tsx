import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError, toFieldErrors } from "../api/ApiError";
import { ErrorCode } from "../api/types";
import { useAuth } from "../auth/useAuth";
import { FormField } from "../components/FormField";
import { useToast } from "../components/useToast";
import {
  trim,
  validateDisplayName,
  validateEmail,
  validatePasswordConfirm,
  validateSignupPassword,
  validateUsername,
} from "./validation";

/**
 * SC-02 新規登録画面（docs/03_screen_design.md）。
 *
 * 登録に成功するとバックエンドがトークンを返すため、
 * <b>そのままログイン状態になる</b>（F-AU-01）。ログイン画面には戻さない。
 *
 * エラーはすべてフィールド単位で出す。409（重複）も同様
 * （409は errors[] を持たないため、code から該当フィールドを決めている）。
 */
export function SignupPage() {
  const { signup } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [username, setUsername] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    // パスワード以外はトリムする（D-27）
    const trimmedEmail = trim(email);
    const trimmedUsername = trim(username);
    const trimmedDisplayName = trim(displayName);

    const errors: Record<string, string> = {};
    const emailError = validateEmail(trimmedEmail);
    const usernameError = validateUsername(trimmedUsername);
    const displayNameError = validateDisplayName(trimmedDisplayName);
    const passwordError = validateSignupPassword(password);
    const confirmError = validatePasswordConfirm(password, passwordConfirm);
    if (emailError) errors.email = emailError;
    if (usernameError) errors.username = usernameError;
    if (displayNameError) errors.displayName = displayNameError;
    if (passwordError) errors.password = passwordError;
    if (confirmError) errors.passwordConfirm = confirmError;

    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      // passwordConfirm は送らない。バックエンドのDTOに存在しない項目のため
      await signup({
        email: trimmedEmail,
        username: trimmedUsername,
        displayName: trimmedDisplayName,
        password,
      });
      navigate("/", { replace: true });
    } catch (error) {
      if (error instanceof ApiError) {
        // 400（errors[]あり）と409（codeのみ）を同じ形に正規化する
        const mapped = toFieldErrors(error);
        if (Object.keys(mapped).length > 0) {
          setFieldErrors(mapped);
        } else {
          showToast(error.message, true);
        }
        if (error.code === ErrorCode.NETWORK_ERROR) {
          showToast("通信に失敗しました。時間をおいて再度お試しください", true);
        }
      } else {
        showToast("通信に失敗しました。時間をおいて再度お試しください", true);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="brand" aria-hidden="true">
          ◉
        </div>
        <h1>アカウントを作成</h1>
        <p className="lead">登録するとそのままログインします</p>

        <form onSubmit={handleSubmit} noValidate>
          <FormField
            id="email"
            name="email"
            label="メールアドレス"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            disabled={isSubmitting}
          />

          <FormField
            id="username"
            name="username"
            label="ユーザー名"
            type="text"
            autoComplete="username"
            maxLength={30}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            error={fieldErrors.username}
            disabled={isSubmitting}
            hint={
              <>
                プレビュー: <strong>@{username || "username"}</strong>
                <br />
                半角英数字とアンダースコアのみ／3〜30文字
              </>
            }
          />

          <FormField
            id="displayName"
            name="displayName"
            label="表示名"
            type="text"
            autoComplete="nickname"
            maxLength={50}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            error={fieldErrors.displayName}
            disabled={isSubmitting}
            hint="1〜50文字。日本語が使えます"
          />

          <FormField
            id="password"
            name="password"
            label="パスワード"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            disabled={isSubmitting}
            hint="8文字以上。英字と数字を含めてください"
          />

          <FormField
            id="passwordConfirm"
            name="passwordConfirm"
            label="パスワード（確認）"
            type="password"
            autoComplete="new-password"
            value={passwordConfirm}
            onChange={(e) => setPasswordConfirm(e.target.value)}
            error={fieldErrors.passwordConfirm}
            disabled={isSubmitting}
          />

          <button className="btn btn-accent btn-block" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "登録中..." : "登録する"}
          </button>
        </form>

        <div className="auth-footer">
          すでにアカウントをお持ちの方は
          <br />
          <Link to="/login">→ ログイン</Link>
        </div>
      </div>
    </div>
  );
}
