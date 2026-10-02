/**
 * E2Eテストが実際に読む項目だけの最小型定義（docs/14_e2e_test.md D-75）。
 *
 * frontend/src/api/types.ts を import しない。技術的には可能だが、
 * E2Eが検証すべきは「フロントが実際に返す形」であり、フロントの型定義を
 * 信じてしまうと型と実装が同時に間違っていたときに検出できないため。
 * 重複にはなるが、frontendの型をE2Eの都合で変更しなくて済む利点もある
 * （perf/lib/types.ts と同じ考え方）。
 */

export type SignupRequest = {
  email: string;
  username: string;
  displayName: string;
  password: string;
};

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: {
    id: number;
    username: string;
    displayName: string;
    avatarUrl: string | null;
  };
};

export type CreatePostRequest = {
  body: string;
};

export type PostSummary = {
  id: number;
  body: string;
  createdAt: string;
};
