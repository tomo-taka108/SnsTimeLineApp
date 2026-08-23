import type { InputHTMLAttributes, ReactNode } from "react";

type Props = {
  /** input の id。label と紐付ける */
  id: string;
  label: string;
  /** バリデーションエラー。あれば入力欄の直下に赤字で出す */
  error?: string;
  /** 補足説明。エラーが無いときだけ出す */
  hint?: ReactNode;
} & InputHTMLAttributes<HTMLInputElement>;

/**
 * ラベル + 入力欄 + エラー/ヒント。
 *
 * エラーは該当フィールドの直下に出す（docs/03_screen_design.md 8章）。
 * すべての項目が必須のため、ラベルには常に必須マーク（CSSの ::after）を付ける。
 */
export function FormField({ id, label, error, hint, ...inputProps }: Props) {
  const hasError = Boolean(error);

  return (
    <div className="form-field">
      <label className="required" htmlFor={id}>
        {label}
      </label>
      <input
        {...inputProps}
        id={id}
        className={hasError ? "input is-error" : "input"}
        aria-invalid={hasError}
        aria-describedby={hasError ? `${id}-error` : undefined}
      />
      {hasError ? (
        <div className="field-error" id={`${id}-error`} role="alert">
          <span aria-hidden="true">⚠️</span>
          <span>{error}</span>
        </div>
      ) : (
        hint && <div className="field-hint">{hint}</div>
      )}
    </div>
  );
}
