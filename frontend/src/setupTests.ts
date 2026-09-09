/**
 * Vitest のセットアップファイル（vite.config.ts の test.setupFiles）。
 *
 * `/vitest` サブパスを使うこと。素の `@testing-library/jest-dom` は Jest 前提で型が付かない。
 *
 * IntersectionObserver のスタブはあえて置かない。jsdom には無いが、それを使う
 * useInfiniteScroll はテスト対象外（docs/11_test_design.md 23.0 / 23.8 #10）。
 * スタブを置くと「なぜ動くのか」が隠れるため。
 */
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
import "@testing-library/jest-dom/vitest";

/**
 * 各テストの後に描画済みのDOMを片付ける。
 *
 * <b>globals: false（明示import方式、D-58）では RTL の自動クリーンアップが効かない。</b>
 * RTL は `afterEach` がグローバルに存在するときだけ自動登録するため、明示import方式では
 * 前のテストの描画結果がDOMに残り続ける。その結果 `getByText` が
 * 「Found multiple elements」で落ちたり、`document.body` の状態が持ち越されたりする
 * （実際に踏んだ）。ここで一度だけ登録しておけば、各テストファイルでは意識しなくてよい。
 */
afterEach(() => {
  cleanup();
});
