import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { ErrorCode } from "../api/types";
import { useAuth } from "../auth/useAuth";
import { FormField } from "../components/FormField";
import { useToast } from "../components/useToast";
import { trim, validateEmail, validateLoginPassword } from "./validation";

/**
 * SC-01 ログイン画面（docs/03_screen_design.md）。
 *
 * 401（認証失敗）は<b>フォーム上部</b>にまとめて出す。
 * 「メールが存在しない」と「パスワードが違う」を区別しないため、
 * どちらのフィールドにも紐付けない（アカウント列挙の防止）。
 */
export function LoginPage() {
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    // パスワードはトリムしない（D-27）。前後の空白も正当な文字のため
    const trimmedEmail = trim(email);

    const errors: Record<string, string> = {};
    const emailError = validateEmail(trimmedEmail);
    const passwordError = validateLoginPassword(password);
    if (emailError) errors.email = emailError;
    if (passwordError) errors.password = passwordError;

    setFieldErrors(errors);
    setFormError(null);
    if (Object.keys(errors).length > 0) return;

    setIsSubmitting(true);
    try {
      await login({ email: trimmedEmail, password });
      navigate("/", { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.code === ErrorCode.INVALID_CREDENTIALS) {
        setFormError("メールアドレスまたはパスワードが正しくありません");
      } else if (error instanceof ApiError && error.code === ErrorCode.NETWORK_ERROR) {
        showToast("通信に失敗しました。時間をおいて再度お試しください", true);
      } else {
        showToast(
          error instanceof ApiError ? error.message : "通信に失敗しました。時間をおいて再度お試しください",
          true,
        );
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
        <h1>ログイン</h1>
        <p className="lead">SnsTimeLineApp</p>

        {formError && (
          <div className="form-alert" role="alert">
            <span aria-hidden="true">⚠️</span>
            <span>{formError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <FormField
            id="email"
            name="email"
            label="メールアドレス"
            type="email"
            autoComplete="email"
            placeholder="you@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            disabled={isSubmitting}
          />

          <FormField
            id="password"
            name="password"
            label="パスワード"
            type="password"
            autoComplete="current-password"
            placeholder="パスワードを入力"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            disabled={isSubmitting}
          />

          <button className="btn btn-accent btn-block" type="submit" disabled={isSubmitting}>
            {isSubmitting ? "ログイン中..." : "ログイン"}
          </button>
        </form>

        <div className="auth-footer">
          アカウントをお持ちでない方は
          <br />
          <Link to="/signup">→ 新規登録</Link>
        </div>
      </div>
    </div>
  );
}
