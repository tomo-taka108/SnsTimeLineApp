/**
 * Vitest のセットアップファイル（vite.config.ts の test.setupFiles）。
 *
 * `/vitest` サブパスを使うこと。素の `@testing-library/jest-dom` は Jest 前提で型が付かない。
 *
 * IntersectionObserver のスタブはあえて置かない。jsdom には無いが、それを使う
 * useInfiniteScroll は本PRの対象外（docs/11_test_design.md 23.0 / 23.8 #10）。
 * スタブを置くと「なぜ動くのか」が隠れるため。
 */
import "@testing-library/jest-dom/vitest";
