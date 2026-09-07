import { describe, expect, it } from "vitest";
import {
  countChars,
  trim,
  validateBio,
  validateCommentBody,
  validateDisplayName,
  validateEmail,
  validateLoginPassword,
  validatePasswordConfirm,
  validatePostBody,
  validateProfileDisplayName,
  validateSignupPassword,
  validateUsername,
} from "./validation";

/**
 * {@link validation.ts} の単体テスト（docs/11_test_design.md 23.1章、ケース #324〜#361）。
 *
 * バックエンドDTOと同じ境界値をフロントにも持っている（3層で検証する方針）。
 * 同じ規則が2箇所にあるということは、片方だけ直す回帰が起こりうるということでもある。
 */
describe("countChars", () => {
  it("#324 ASCII文字はそのまま数える", () => {
    expect(countChars("abc")).toBe(3);
  });

  it("#325 絵文字1個は1文字と数える（.length は2）", () => {
    expect(countChars("👍")).toBe(1);
    expect("👍".length).toBe(2);
  });

  it('#326 "あ"×279+絵文字1個は280（.length では281）', () => {
    const value = "あ".repeat(279) + "😀";
    expect(countChars(value)).toBe(280);
    expect(value.length).toBe(281);
  });
});

describe("trim", () => {
  it("#327 前後の空白を取り除く", () => {
    expect(trim("  ab  ")).toBe("ab");
  });

  it("#328 空白のみは空文字になる", () => {
    expect(trim("   ")).toBe("");
  });
});

describe("validateEmail", () => {
  it("#329 空文字は必須エラー", () => {
    expect(validateEmail("")).toBe("メールアドレスを入力してください");
  });

  it("#330 @が無ければ形式エラー", () => {
    expect(validateEmail("no-at.example.com")).toBe("メールアドレスの形式が正しくありません");
  });

  it("#331 ドメインにドットが無ければ形式エラー", () => {
    expect(validateEmail("no-dot@example")).toBe("メールアドレスの形式が正しくありません");
  });

  it("#332 255文字（上限ちょうど）は通る", () => {
    const local = "a".repeat(243);
    const email = `${local}@example.com`; // 243 + 1 + 11 = 255
    expect(email.length).toBe(255);
    expect(validateEmail(email)).toBeUndefined();
  });

  it("#333 256文字（上限+1）は文字数エラー", () => {
    const local = "a".repeat(244);
    const email = `${local}@example.com`; // 256
    expect(email.length).toBe(256);
    expect(validateEmail(email)).toBe("メールアドレスは255文字以内で入力してください");
  });

  it("#334 正しい形式は通る", () => {
    expect(validateEmail("taro@example.com")).toBeUndefined();
  });
});

describe("validateLoginPassword", () => {
  it("#335 空文字は必須エラー", () => {
    expect(validateLoginPassword("")).toBe("パスワードを入力してください");
  });

  it("#336 1文字でも通る（ポリシーを漏らさない設計）", () => {
    expect(validateLoginPassword("a")).toBeUndefined();
  });
});

describe("validateUsername", () => {
  it("#337 空文字は必須エラー", () => {
    expect(validateUsername("")).toBe("ユーザー名を入力してください");
  });

  it("#338 2文字（下限-1）は文字数エラー", () => {
    expect(validateUsername("ab")).toBe("ユーザー名は3〜30文字で入力してください");
  });

  it("#339 3文字（下限）は通る", () => {
    expect(validateUsername("abc")).toBeUndefined();
  });

  it("#340 30文字（上限）は通る", () => {
    expect(validateUsername("a".repeat(30))).toBeUndefined();
  });

  it("#341 31文字（上限+1）は文字数エラー", () => {
    expect(validateUsername("a".repeat(31))).toBe("ユーザー名は3〜30文字で入力してください");
  });

  it("#342 記号（ハイフン）は形式エラー", () => {
    expect(validateUsername("taro-123")).toBe("ユーザー名は半角英数字とアンダースコアのみ使用できます");
  });
});

describe("validateDisplayName", () => {
  it("#343 空文字は必須エラー", () => {
    expect(validateDisplayName("")).toBe("表示名を入力してください");
  });

  it("#344 50文字（上限）は通る", () => {
    expect(validateDisplayName("あ".repeat(50))).toBeUndefined();
  });

  it("#345 51文字（上限+1）は文字数エラー", () => {
    expect(validateDisplayName("あ".repeat(51))).toBe("表示名は1〜50文字で入力してください");
  });
});

describe("validateSignupPassword", () => {
  it("#346 空文字は必須エラー", () => {
    expect(validateSignupPassword("")).toBe("パスワードを入力してください");
  });

  it("#347 7文字（下限-1）はポリシーエラー", () => {
    expect(validateSignupPassword("abcdef1")).toBe("パスワードは8文字以上で、英字と数字を含めてください");
  });

  it("#348 8文字（下限）で英数字混在なら通る", () => {
    expect(validateSignupPassword("abcdefg1")).toBeUndefined();
  });

  it("#349 英字のみ8文字はポリシーエラー（数字が無い）", () => {
    expect(validateSignupPassword("abcdefgh")).toBe("パスワードは8文字以上で、英字と数字を含めてください");
  });
});

describe("validatePasswordConfirm", () => {
  it("#350 確認欄が空なら必須エラー", () => {
    expect(validatePasswordConfirm("p", "")).toBe("確認用のパスワードを入力してください");
  });

  it("#351 不一致なら一致エラー", () => {
    expect(validatePasswordConfirm("p1", "p2")).toBe("パスワードが一致しません");
  });

  it("#352 一致すれば通る", () => {
    expect(validatePasswordConfirm("p1", "p1")).toBeUndefined();
  });
});

describe("validatePostBody", () => {
  it("#353 空白のみは必須エラー（trim後に判定）", () => {
    expect(validatePostBody("   ")).toBe("本文を入力してください");
  });

  it("#354 280文字（上限）は通る", () => {
    expect(validatePostBody("あ".repeat(280))).toBeUndefined();
  });

  it("#355 281文字（上限+1）は文字数エラー", () => {
    expect(validatePostBody("あ".repeat(281))).toBe("本文は280文字以内で入力してください");
  });

  /**
   * #356 未トリムの文字数を280と比べる。trim後は279文字でも、前後の空白を含めた
   * 281文字がそのままカウントされ弾かれる。PostComposer は送信時に trim() するため
   * 実害は軽微（安全側に倒れている）だが、現状の挙動として固定する。
   */
  it("#356 前後空白込みで281文字（trim後279文字）は弾かれる", () => {
    const value = ` ${"あ".repeat(279)} `; // 前後空白+279 = 281
    expect(value.length).toBe(281);
    expect(trim(value).length).toBe(279);
    expect(validatePostBody(value)).toBe("本文は280文字以内で入力してください");
  });
});

describe("validateCommentBody", () => {
  it("#357 空白のみは必須エラー（投稿とはメッセージ文言が異なる）", () => {
    expect(validateCommentBody("   ")).toBe("コメントを入力してください");
  });

  it("#357b 280文字（上限）は通り、281文字（上限+1）は文字数エラー", () => {
    expect(validateCommentBody("あ".repeat(280))).toBeUndefined();
    expect(validateCommentBody("あ".repeat(281))).toBe("コメントは280文字以内で入力してください");
  });
});

/**
 * #358 validateDisplayName（新規登録）は `!value`、こちらは `!trim(value)` で判定する。
 * 前者は空白のみを通してしまうが、後者は弾く。バグではなく、呼び出し側（SignupPage.tsx）
 * が渡す前にトリム済みの値を渡すため実害が無い（D-27）。この非対称を対で固定する。
 */
describe("validateProfileDisplayName", () => {
  it("#358 空白のみは必須エラー（#343のvalidateDisplayNameとは非対称）", () => {
    expect(validateProfileDisplayName("   ")).toBe("表示名を入力してください");
    // 対になるケース: validateDisplayName は同じ入力を通してしまう
    expect(validateDisplayName("   ")).toBeUndefined();
  });

  it("#358b 50文字（上限）は通り、51文字（上限+1）は文字数エラー", () => {
    expect(validateProfileDisplayName("あ".repeat(50))).toBeUndefined();
    expect(validateProfileDisplayName("あ".repeat(51))).toBe("表示名は1〜50文字で入力してください");
  });
});

describe("validateBio", () => {
  it("#359 空文字は通る（任意項目）", () => {
    expect(validateBio("")).toBeUndefined();
  });

  it("#360 160文字（上限）は通る", () => {
    expect(validateBio("あ".repeat(160))).toBeUndefined();
  });

  it("#361 161文字（上限+1）は文字数エラー", () => {
    expect(validateBio("あ".repeat(161))).toBe("自己紹介は160文字以内で入力してください");
  });
});
