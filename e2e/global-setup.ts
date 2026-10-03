/**
 * Playwright の globalSetup（docs/14_e2e_test.md 2.2）。
 *
 * 実行開始時に前回の残骸を掃除する。これは「前掃除」であり、実行後の
 * 「後掃除」は e2e/run-e2e.sh の trap が担う（globalTeardown 単独には
 * 頼らない。globalSetup が例外を投げると globalTeardown は呼ばれず、
 * これは k6 の teardown() と同じ落とし穴のため。D-66 と同じ判断）。
 */

import { truncateAll } from "./fixtures/api.ts";

export default async function globalSetup(): Promise<void> {
  await truncateAll();
}
