import { ApiError } from "./ApiError";
import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from "./tokenStorage";
import type { ErrorResponse, TokenResponse } from "./types";

/**
 * APIクライアントの共通ラッパー（docs/07_architecture.md 7章）。
 *
 * 役割は2つ:
 * 1. アクセストークンを自動で付ける
 * 2. 401を受けたらトークンを再発行し、元のリクエストを1回だけ再試行する
 *
 * <b>401の処理を各画面に書かない。</b> ここ1箇所に集約する。
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";

/** 認証が切れて復帰できなかったときに呼ばれる。AuthContext が登録する */
type SessionExpiredHandler = () => void;
let onSessionExpired: SessionExpiredHandler | null = null;

export function setSessionExpiredHandler(handler: SessionExpiredHandler | null): void {
  onSessionExpired = handler;
}

/**
 * 進行中のリフレッシュ。
 *
 * <b>モジュールスコープに1つだけ持つのが要点。</b>
 * 複数のAPIが同時に401になったとき、それぞれが個別にリフレッシュすると、
 * ローテーションにより2回目以降が「使用済みトークンの再提示」になり、
 * バックエンドに盗用と判定されてファミリー全体が失効する（＝強制ログアウト）。
 * 症状が「たまに勝手にログアウトする」という分かりにくい形で出るため、
 * 進行中のPromiseを共有して1回にまとめる。
 */
let refreshing: Promise<string> | null = null;

type RequestOptions = {
  method?: string;
  body?: unknown;
  /**
   * 認証不要のエンドポイント（login / signup / refresh）。
   *
   * これらの401は「認証情報が違う」という意味であり、
   * トークンの期限切れではない。リフレッシュの対象にしない。
   */
  public?: boolean;
  /**
   * 画像アップロード（#25）用。multipart/form-data で送る。
   *
   * Content-Type は<b>あえて設定しない</b>。boundary 付きのヘッダーは
   * ブラウザが FormData から自動生成するため、手で指定すると壊れる。
   */
  formData?: FormData;
  /**
   * リクエストの中断用（#20 ユーザー検索）。
   *
   * 「ta」→「taro」と続けて入力したとき、ネットワーク次第で「ta」の結果が後から届いて
   * 表示を上書きすることがある（docs/03_screen_design.md SC-07）。
   * 古いリクエストを呼び出し側から中断できるようにする。
   *
   * 中断時 fetch は AbortError を投げる。呼び出し側で握りつぶすこと
   * （ここでは ApiError.network() に変換されるため、通信エラーと区別がつかない）。
   */
  signal?: AbortSignal;
};

/** レスポンスをエラーとして解釈する。ボディが壊れていても落ちないようにする */
async function toApiError(response: Response): Promise<ApiError> {
  let body: Partial<ErrorResponse> = {};
  try {
    body = (await response.json()) as Partial<ErrorResponse>;
  } catch {
    // ボディが無い、またはJSONでない場合はステータスだけで判断する
  }
  return new ApiError(
    response.status,
    body.code ?? "INTERNAL_ERROR",
    body.message ?? "通信に失敗しました。時間をおいて再度お試しください",
    body.errors ?? [],
  );
}

/**
 * リフレッシュトークンで新しいトークンを取得する。
 *
 * 成功したら新しいアクセストークンを返し、両方のトークンを保存し直す。
 */
async function doRefresh(): Promise<string> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new ApiError(401, "INVALID_REFRESH_TOKEN", "セッションの有効期限が切れました");
  }

  let response: Response;
  try {
    response = await fetch(`${BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    throw ApiError.network();
  }

  if (!response.ok) {
    // ここで失敗したら、この処理を再帰的に適用しない（無限ループの防止）
    throw await toApiError(response);
  }

  const tokens = (await response.json()) as TokenResponse;
  // 返ってきた refreshToken を必ず保存し直す（古い値は使用済みになっている）
  saveTokens(tokens.accessToken, tokens.refreshToken);
  return tokens.accessToken;
}

/** 進行中のリフレッシュがあればそれを待ち、無ければ開始する */
function refreshOnce(): Promise<string> {
  if (!refreshing) {
    refreshing = doRefresh().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/** 実際にfetchを投げる。トークンは呼び出し側から渡す */
async function send(path: string, options: RequestOptions, accessToken: string | null): Promise<Response> {
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (accessToken && !options.public) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  try {
    return await fetch(`${BASE_URL}${path}`, {
      method: options.method ?? "GET",
      headers,
      body: options.formData ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
      signal: options.signal,
    });
  } catch (error) {
    // 中断（AbortController）は「失敗」ではないのでそのまま投げ直す。
    // ApiError.network() に変換すると、呼び出し側が通信エラーと区別できず
    // 「入力するたびにエラー表示が出る」ことになる
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    // ネットワーク到達不能（サーバー停止・オフライン）
    throw ApiError.network();
  }
}

/**
 * APIを呼ぶ。
 *
 * @throws {ApiError} 通信失敗、またはサーバーがエラーを返した場合
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await send(path, options, getAccessToken());

  // 401 かつ 認証が必要なエンドポイントなら、トークンを再発行して1回だけ再試行する
  if (response.status === 401 && !options.public) {
    let newAccessToken: string;
    try {
      newAccessToken = await refreshOnce();
    } catch {
      // 再発行できなかった＝セッション終了。両トークンを消してログイン画面へ
      clearTokens();
      onSessionExpired?.();
      throw new ApiError(401, "INVALID_REFRESH_TOKEN", "セッションの有効期限が切れました");
    }

    // 再試行は1回だけ。ここでまた401でも、再度リフレッシュはしない
    response = await send(path, options, newAccessToken);

    if (response.status === 401) {
      clearTokens();
      onSessionExpired?.();
      throw new ApiError(401, "UNAUTHENTICATED", "セッションの有効期限が切れました");
    }
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  // 204 No Content（ログアウト）はボディが無い。json() を呼ぶと落ちる
  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
