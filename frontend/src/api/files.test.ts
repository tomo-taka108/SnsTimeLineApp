import { describe, expect, it } from "vitest";
import { fileUrl, resolveFileUrl } from "./files";

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
