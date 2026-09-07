import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { formatAbsolute, formatJoined, formatRelative } from "./datetime";

/**
 * {@link datetime.ts} の単体テスト（docs/11_test_design.md 23.2章、ケース #362〜#376）。
 *
 * Date.now() を使う関数はそのままではテストできない。vi.setSystemTime で時計を止め、
 * 基準時刻を 2026-08-15T14:32:00+09:00（Asia/Tokyo）に固定して検証する。
 */
describe("datetime", () => {
  const BASE = new Date("2026-08-15T14:32:00+09:00");

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(BASE);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /**
   * #362 前提の明示。formatAbsolute / formatJoined はローカルタイムゾーン依存の
   * getHours() 等を使う。TZ固定（vite.config.ts の test.env）が効いていることを
   * ここで確認しないと、以降の全ケースが環境依存のまま通ってしまう（実測確認済み:
   * 同じ瞬間が Asia/Tokyo なら 14:32、UTC なら 5:32）。
   */
  it("#362 TZ が Asia/Tokyo に固定されている", () => {
    expect(new Date("2026-08-15T05:32:00Z").getHours()).toBe(14);
  });

  describe("formatRelative", () => {
    it("#363 0秒前は「たった今」", () => {
      expect(formatRelative(BASE.toISOString())).toBe("たった今");
    });

    it("#364 59秒前は「たった今」", () => {
      const iso = new Date(BASE.getTime() - 59_000).toISOString();
      expect(formatRelative(iso)).toBe("たった今");
    });

    it("#365 60秒前（1分）は「1分前」", () => {
      const iso = new Date(BASE.getTime() - 60_000).toISOString();
      expect(formatRelative(iso)).toBe("1分前");
    });

    it("#366 59分前は「59分前」", () => {
      const iso = new Date(BASE.getTime() - 59 * 60_000).toISOString();
      expect(formatRelative(iso)).toBe("59分前");
    });

    it("#367 60分前（1時間）は「1時間前」", () => {
      const iso = new Date(BASE.getTime() - 60 * 60_000).toISOString();
      expect(formatRelative(iso)).toBe("1時間前");
    });

    it("#368 23時間59分前は「23時間前」", () => {
      const iso = new Date(BASE.getTime() - (23 * 60 + 59) * 60_000).toISOString();
      expect(formatRelative(iso)).toBe("23時間前");
    });

    it("#369 24時間前は絶対表示（8月14日）に切り替わる", () => {
      const iso = new Date(BASE.getTime() - 24 * 60 * 60_000).toISOString();
      expect(formatRelative(iso)).toBe("8月14日");
    });

    it("#370 数日前は月日表示", () => {
      const iso = new Date(BASE.getTime() - 3 * 24 * 60 * 60_000).toISOString();
      expect(formatRelative(iso)).toBe("8月12日");
    });

    /**
     * #371 未来の時刻（時計ずれ等で diffMs が負になる場合）。
     * 現状の実装は min が負でも `min < 1` を満たすため「たった今」になる。
     * バグとして直すかは別途判断するが、まずは現状の挙動として固定する。
     */
    it("#371 未来の時刻は「たった今」になる（現状の挙動）", () => {
      const iso = new Date(BASE.getTime() + 60_000).toISOString();
      expect(formatRelative(iso)).toBe("たった今");
    });
  });

  describe("formatAbsolute", () => {
    it("#372 時刻はゼロ埋めされる", () => {
      // UTC 00:05 = JST 09:05
      expect(formatAbsolute("2026-08-15T00:05:00Z")).toBe("2026年8月15日 09:05");
    });

    it("#373 UTCからJSTへの変換で日付が繰り上がる", () => {
      // UTC 23:00 = JST 翌8:00
      expect(formatAbsolute("2026-08-15T23:00:00Z")).toBe("2026年8月16日 08:00");
    });

    it("#374 月が1桁のときはゼロ埋めしない", () => {
      expect(formatAbsolute("2026-08-15T00:05:00Z")).toContain("8月");
    });
  });

  describe("formatJoined", () => {
    it("#375 通常の月表示", () => {
      expect(formatJoined("2026-08-15T00:00:00Z")).toBe("2026年8月からご利用");
    });

    it("#376 年をまたぐ場合（UTC 12/31 23:00 = JST 翌1/1）", () => {
      expect(formatJoined("2026-12-31T23:00:00Z")).toBe("2027年1月からご利用");
    });
  });
});
