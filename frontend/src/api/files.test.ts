import { describe, expect, it } from "vitest";
import {
  ALLOWED_IMAGE_TYPES,
  MAX_IMAGE_SIZE_BYTES,
  fileUrl,
  resolveFileUrl,
  validateImageFile,
} from "./files";

/**
 * {@link files.ts} の resolveFileUrl / fileUrl の単体テスト
 * （docs/11_test_design.md 23.4章、ケース #386〜#389）。
 *
 * BASE_URL は import.meta.env.VITE_API_BASE_URL をモジュール読み込み時に読むが、
 * Vitest は test モードのため未定義になり、フォールバック値
 * "http://localhost:8080/api/v1" が確定的に使われる（09_decision_log.md D-47）。
 */
describe("resolveFileUrl", () => {
  it("#386 null はそのまま null を返す", () => {
    expect(resolveFileUrl(null)).toBeNull();
  });

  it("#387 パスにオリジンが付いた絶対URLになる", () => {
    expect(resolveFileUrl("/api/v1/files/1")).toBe("http://localhost:8080/api/v1/files/1");
  });

  it("#388 空文字はオリジンのみの文字列になる（現状の挙動）", () => {
    expect(resolveFileUrl("")).toBe("http://localhost:8080");
  });
});

describe("fileUrl", () => {
  it("#389 BASE_URLに /files/{id} を連結する", () => {
    expect(fileUrl(1)).toBe("http://localhost:8080/api/v1/files/1");
  });
});

/**
 * {@link validateImageFile} の単体テスト（docs/11_test_design.md 23.4章、ケース #396b〜#396g）。
 *
 * PostComposer・プロフィール編集（アバター／カバー）の3箇所に同じ検証がインラインで
 * 重複していたものを共通関数に切り出した（Issue #47）。3箇所が同じ規則で動くことを
 * ここ1箇所で固定する。
 *
 * 5MB ちょうどは通り、1バイト超えると弾かれる（境界値分析）。
 */
describe("validateImageFile", () => {
  /** File の中身は読まないため、サイズだけを持つ軽量なダミーで足りる */
  function file(type: string, size: number): File {
    const f = new File([], "image", { type });
    Object.defineProperty(f, "size", { value: size });
    return f;
  }

  it.each(ALLOWED_IMAGE_TYPES)("#396b 許可形式 %s はサイズが範囲内なら undefined", (type) => {
    expect(validateImageFile(file(type, 1024))).toBeUndefined();
  });

  it("#396c 許可されていない形式はエラーメッセージを返す", () => {
    expect(validateImageFile(file("image/gif", 1024))).toBe(
      "対応していないファイル形式です（JPEG / PNG / WebP のみ）",
    );
  });

  it("#396d 形式が空文字でも弾く（type を取得できない場合）", () => {
    expect(validateImageFile(file("", 1024))).toBe(
      "対応していないファイル形式です（JPEG / PNG / WebP のみ）",
    );
  });

  it("#396e 5MBちょうどは通る（境界内）", () => {
    expect(validateImageFile(file("image/png", MAX_IMAGE_SIZE_BYTES))).toBeUndefined();
  });

  it("#396f 5MB+1バイトは弾く（境界外）", () => {
    expect(validateImageFile(file("image/png", MAX_IMAGE_SIZE_BYTES + 1))).toBe(
      "ファイルサイズが大きすぎます（5MBまで）",
    );
  });

  /**
   * #396g 形式とサイズの両方が不正なら、形式のエラーが返る。
   * 実装が形式を先に判定していることの固定（メッセージが入れ替わると利用者が混乱する）。
   */
  it("#396g 形式とサイズの両方が不正なら形式のエラーを優先する", () => {
    expect(validateImageFile(file("image/gif", MAX_IMAGE_SIZE_BYTES + 1))).toBe(
      "対応していないファイル形式です（JPEG / PNG / WebP のみ）",
    );
  });
});
