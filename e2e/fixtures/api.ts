/**
 * APIでテストデータを作るヘルパー（docs/14_e2e_test.md 3章 / D-75）。
 *
 * perf（generate_series の SQL で10,000件）とは正反対の方針。
 * E2Eに必要なデータは数件〜25件なので、実際のAPIを叩いて作る。
 * これによりテストが前提とする状態を、アプリのロジックを通した
 * 手順で保証できる（バリデーションを素通りしたデータにならない）。
 *
 * fetch を直接使う（Playwright の APIRequestContext ではなく）。
 * 理由: BASE_URL・ヘッダーの組み立てを auth.ts / 本ファイルに集約し、
 * spec側では「何を作ったか」だけを意識すればよい形にするため。
 */

import type { AuthResponse, CreatePostRequest, PostSummary, SignupRequest } from "./types.ts";

export const BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:8080/api/v1";

/** CLAUDE.md 6章: 実在の個人名・実在するメールアドレスを使わない（example.comドメイン） */
export const E2E_PASSWORD = "E2ETest123!";

/**
 * ユニークなメールアドレスを組み立てる。
 *
 * parallelIndex を含めることで、並列ワーカー間でのユーザー衝突を避ける
 * （docs/14_e2e_test.md 3.1）。
 */
export function uniqueEmail(purpose: string, parallelIndex: number): string {
  const stamp = Date.now().toString(36);
  return `e2e-${purpose}-${parallelIndex}-${stamp}@example.com`;
}

export type SignedUpUser = {
  accessToken: string;
  refreshToken: string;
  userId: number;
  username: string;
  email: string;
};

/**
 * 新規登録してトークンを受け取る。
 *
 * signup のレスポンスは即ログイン状態を含む（AuthResponse）ため、
 * 別途 login を呼ぶ必要がない（frontend/src/api/auth.ts と同じ仕様）。
 */
export async function signup(purpose: string, parallelIndex: number): Promise<SignedUpUser> {
  const email = uniqueEmail(purpose, parallelIndex);
  const username = `e2e${purpose}${parallelIndex}${Date.now().toString(36)}`.slice(0, 30);

  const payload: SignupRequest = {
    email,
    username,
    displayName: `E2Eテストユーザー(${purpose})`,
    password: E2E_PASSWORD,
  };

  const res = await fetch(`${BASE_URL}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    throw new Error(`signup failed: status=${res.status} body=${await res.text()}`);
  }

  const body = (await res.json()) as AuthResponse;
  return {
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
    userId: body.user.id,
    username: body.user.username,
    email,
  };
}

/** Authorization ヘッダを組み立てる */
export function authHeaders(accessToken: string): Record<string, string> {
  return {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json",
  };
}

/** 投稿を1件作る */
export async function createPost(accessToken: string, body: string): Promise<PostSummary> {
  const payload: CreatePostRequest = { body };
  const res = await fetch(`${BASE_URL}/posts`, {
    method: "POST",
    headers: authHeaders(accessToken),
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    throw new Error(`createPost failed: status=${res.status} body=${await res.text()}`);
  }

  return (await res.json()) as PostSummary;
}

/**
 * 投稿を複数件作る(直列)。
 *
 * 無限スクロールの検証には「1ページ(20件)を超える投稿」が要る
 * (docs/14_e2e_test.md 3章)。並列に投げると作成順序が不定になり、
 * createdAt の並びに依存するタイムライン表示の検証がぶれるため、直列で作る。
 */
export async function createPosts(accessToken: string, count: number, bodyPrefix: string): Promise<PostSummary[]> {
  const posts: PostSummary[] = [];
  for (let i = 1; i <= count; i++) {
    posts.push(await createPost(accessToken, `${bodyPrefix} ${i}/${count}`));
  }
  return posts;
}

/** 全テーブルを空にする(TRUNCATE)。globalSetup から呼ぶ(docs/14_e2e_test.md 2.2) */
export async function truncateAll(): Promise<void> {
  const { execFile } = await import("node:child_process");
  const { promisify } = await import("node:util");
  const run = promisify(execFile);

  await run("docker", [
    "compose",
    "exec",
    "-T",
    "db-e2e",
    "psql",
    "-U",
    "snsapp",
    "-d",
    "snstimeline_e2e",
    "-v",
    "ON_ERROR_STOP=1",
    "-q",
    "-f",
    "/e2e-sql/truncate-all.sql",
  ]);
}
