/**
 * 負荷試験の認証ヘルパー（docs/13_performance_test.md）。
 *
 * 【リフレッシュトークンを使わない理由】
 * リフレッシュトークンは**使い捨てローテーション**で、同じものを並列で
 * 使い回すと同ファミリ全体が失効する（401 が連鎖する）。
 * アクセストークンの有効期限は15分あり、本試験は約3分で終わるため、
 * setup() で1回ログインしたトークンを使い回すだけで足りる。
 */

import http from 'k6/http';
import { check } from 'k6';
import type { AuthResponse, LoginRequest } from './types.ts';

/** シードが作るユーザーの共通パスワード（perf/seed/01_users.sql と一致させること） */
const SEED_PASSWORD = 'PerfTest123';

/** シードが作るユーザー数（perf/seed/01_users.sql と一致させること） */
export const SEED_USER_COUNT = 100;

/** n 番目のシードユーザーのメールアドレスを組み立てる */
export function seedUserEmail(n: number): string {
  const idx = ((n - 1) % SEED_USER_COUNT) + 1;
  return `perfuser${String(idx).padStart(4, '0')}@example.com`;
}

/**
 * ログインしてアクセストークンを取得する。
 * setup() から呼ぶことを想定（VU ごとに毎回ログインすると認証が負荷の主成分になる）。
 */
export function login(baseUrl: string, email: string): string {
  const payload: LoginRequest = { email, password: SEED_PASSWORD };

  const res = http.post(`${baseUrl}/auth/login`, JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /auth/login' },
  });

  const ok = check(res, {
    'login: status is 200': (r) => r.status === 200,
  });

  if (!ok) {
    // ここで落ちるのはシード未投入かパスワード不一致。
    // メールアドレスは機密情報なのでログに出さない（CLAUDE.md 6章）。
    throw new Error(
      `ログインに失敗した（status=${res.status}）。` +
        'シードが投入されているか、アプリが負荷試験用DBに向いているかを確認すること。'
    );
  }

  const body = res.json() as unknown as AuthResponse;
  return body.accessToken;
}

/** Authorization ヘッダを組み立てる */
export function authHeaders(token: string): Record<string, string> {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}
