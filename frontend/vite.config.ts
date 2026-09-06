/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    globals: false,
    // formatAbsolute / formatJoined はローカルTZ依存。固定しないと環境ごとに結果が変わる
    // （実測確認済み: 同じ瞬間が Asia/Tokyo なら "14:32"、UTC なら "5:32"）。D-57 参照
    env: { TZ: 'Asia/Tokyo' },
    // 前のテストが差し替えた fetch / env / mock が次に漏れるのを防ぐ（D-56 のロールバックと同じ発想）
    restoreMocks: true,
    unstubEnvs: true,
    unstubGlobals: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.ts', 'src/**/*.tsx'],
    },
  },
})
