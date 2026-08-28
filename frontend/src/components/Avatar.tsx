import type { UserSummary } from "../api/types";

type Props = {
  user: UserSummary;
  size?: "sm" | "md" | "lg";
};

/**
 * アバター。画像が未設定ならイニシャルを表示する（docs/03_screen_design.md 5章）。
 *
 * avatarUrl はファイルモジュールが未実装のため現状は常に null だが、
 * 実装されたときに差し替えなくて済むよう、画像の分岐は先に書いておく。
 */
const SIZE_PX: Record<NonNullable<Props["size"]>, number> = { sm: 34, md: 44, lg: 88 };

export function Avatar({ user, size = "md" }: Props) {
  const className = size === "md" ? "avatar" : `avatar avatar-${size}`;

  if (user.avatarUrl) {
    const px = SIZE_PX[size];
    return <img className={className} src={user.avatarUrl} alt="" width={px} height={px} />;
  }

  // 表示名の1文字目。絵文字が壊れないようコードポイント単位で取り出す
  const initial = [...user.displayName][0] ?? "?";

  return (
    <span className={className} aria-hidden="true">
      {initial}
    </span>
  );
}
