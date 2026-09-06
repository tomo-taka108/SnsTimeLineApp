import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * {@link client.ts} の単体テスト（docs/11_test_design.md 23.5章、ケース #397〜#412）。
 *
 * バックエンドの RefreshTokenServiceTest #177〜#186 と対になる、フロント側の要。
 * refreshing と onSessionExpired はモジュールスコープの変数のため、テスト間で
 * 持ち越される。各テストで vi.resetModules() を呼び、動的 import で読み直す
 * （静的importだとモジュールは一度しか評価されないため効かない）。
 *
 * fake timers は使わない。vi.useFakeTimers() を有効にしたまま await fetch を
 * 待つと Promise が解決されないことがあるため（datetime.test.ts とはファイルを分けている）。
 */

type ClientModule = typeof import("./client");
type TokenStorageModule = typeof import("./tokenStorage");

async function freshClient(): Promise<{ client: ClientModule; tokenStorage: TokenStorageModule }> {
  vi.resetModules();
  const client = await import("./client");
  const tokenStorage = await import("./tokenStorage");
  return { client, tokenStorage };
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("client.request", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("#397 トークンがあれば Authorization ヘッダーが付く", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("access-1", "refresh-1");

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await client.request("/posts");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer access-1");
  });

  it("#398 トークンが無ければ Authorization ヘッダーを付けない", async () => {
    const { client } = await freshClient();

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await client.request("/posts");

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("#399 public: true のときはトークンがあっても Authorization を付けない", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("access-1", "refresh-1");

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await client.request("/auth/login", { method: "POST", public: true, body: {} });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });

  it("#400 204 No Content は json() を呼ばず undefined を返す", async () => {
    const { client } = await freshClient();

    const response = new Response(null, { status: 204 });
    const jsonSpy = vi.spyOn(response, "json");
    const fetchMock = vi.fn().mockResolvedValue(response);
    vi.stubGlobal("fetch", fetchMock);

    const result = await client.request("/auth/logout", { method: "POST" });

    expect(result).toBeUndefined();
    expect(jsonSpy).not.toHaveBeenCalled();
  });

  it("#401 body を渡すと Content-Type: application/json が付く", async () => {
    const { client } = await freshClient();

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, { id: 1 }));
    vi.stubGlobal("fetch", fetchMock);

    await client.request("/posts", { method: "POST", body: { body: "こんにちは" } });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });

  /** #402 formData のときは Content-Type を手で設定しない。ブラウザが boundary 付きで自動生成するため */
  it("#402 formData 指定時は Content-Type を設定しない", async () => {
    const { client } = await freshClient();

    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, { fileId: 1 }));
    vi.stubGlobal("fetch", fetchMock);

    const formData = new FormData();
    formData.append("file", new File(["x"], "a.png"));
    await client.request("/files", { method: "POST", formData });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Content-Type"]).toBeUndefined();
  });

  it("#403 エラーレスポンスのJSONボディを ApiError に反映する", async () => {
    const { client } = await freshClient();

    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(500, { code: "INTERNAL_ERROR", message: "サーバーエラー" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/posts")).rejects.toMatchObject({
      status: 500,
      code: "INTERNAL_ERROR",
      message: "サーバーエラー",
    });
  });

  it("#404 エラーボディがJSONでない場合は INTERNAL_ERROR にフォールバックする", async () => {
    const { client } = await freshClient();

    const response = new Response("not json", { status: 500 });
    const fetchMock = vi.fn().mockResolvedValue(response);
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/posts")).rejects.toMatchObject({
      status: 500,
      code: "INTERNAL_ERROR",
      message: "通信に失敗しました。時間をおいて再度お試しください",
    });
  });

  it("#405 fetch自体がrejectすると ApiError.network() になる", async () => {
    const { client } = await freshClient();

    const fetchMock = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/posts")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" });
  });

  /**
   * #406 中断（AbortController）は「失敗」ではないため、そのまま投げ直す。
   * ApiError.network() に変換すると、useUserSearch が入力のたびにエラー表示を出す。
   */
  it("#406 AbortError はそのまま再throwされ、ApiErrorに変換されない", async () => {
    const { client } = await freshClient();

    const abortError = new DOMException("aborted", "AbortError");
    const fetchMock = vi.fn().mockRejectedValue(abortError);
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/users")).rejects.toBe(abortError);
  });

  it("#407 401かつrefresh成功時、新トークンで1回だけ再試行して成功する", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("old-access", "old-refresh");

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" })) // 元のリクエスト
      .mockResolvedValueOnce(
        jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh", expiresIn: 900 }),
      ) // refresh
      .mockResolvedValueOnce(jsonResponse(200, { items: [] })); // 再試行
    vi.stubGlobal("fetch", fetchMock);

    const result = await client.request("/posts");

    expect(result).toEqual({ items: [] });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const [, retryInit] = fetchMock.mock.calls[2] as [string, RequestInit];
    expect((retryInit.headers as Record<string, string>).Authorization).toBe("Bearer new-access");
  });

  it("#408 refresh成功時、新しい2トークンがlocalStorageに保存される", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("old-access", "old-refresh");

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" }))
      .mockResolvedValueOnce(
        jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh", expiresIn: 900 }),
      )
      .mockResolvedValueOnce(jsonResponse(200, { items: [] }));
    vi.stubGlobal("fetch", fetchMock);

    await client.request("/posts");

    expect(tokenStorage.getAccessToken()).toBe("new-access");
    expect(tokenStorage.getRefreshToken()).toBe("new-refresh");
  });

  it("#409 再試行後にまた401なら、2回目のrefreshはせずセッション終了として扱う", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("old-access", "old-refresh");
    const onSessionExpired = vi.fn();
    client.setSessionExpiredHandler(onSessionExpired);

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" })) // 元のリクエスト
      .mockResolvedValueOnce(
        jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh", expiresIn: 900 }),
      ) // refresh 成功
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" })); // 再試行もまた401
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/posts")).rejects.toMatchObject({ code: "UNAUTHENTICATED" });

    // refreshは1回だけ（2回目の401で再帰的にrefreshしない）
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalledOnce();
  });

  it("#410 refreshTokenが無ければrefreshを呼ばずセッション終了として扱う", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("old-access", "dummy");
    localStorage.removeItem("snstimeline.refreshToken"); // refreshTokenだけ無い状態を作る

    const onSessionExpired = vi.fn();
    client.setSessionExpiredHandler(onSessionExpired);

    const fetchMock = vi.fn().mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/posts")).rejects.toMatchObject({ code: "INVALID_REFRESH_TOKEN" });

    // 元のリクエスト1回のみ。refreshエンドポイントは呼ばれない
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onSessionExpired).toHaveBeenCalledOnce();
  });

  it("#411 refresh自体が401ならセッション終了として扱い、再帰しない", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("old-access", "old-refresh");
    const onSessionExpired = vi.fn();
    client.setSessionExpiredHandler(onSessionExpired);

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { code: "UNAUTHENTICATED" })) // 元のリクエスト
      .mockResolvedValueOnce(jsonResponse(401, { code: "INVALID_REFRESH_TOKEN" })); // refreshも401
    vi.stubGlobal("fetch", fetchMock);

    await expect(client.request("/posts")).rejects.toMatchObject({ code: "INVALID_REFRESH_TOKEN" });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(tokenStorage.getAccessToken()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalledOnce();
  });

  /**
   * #412 本章の中心。複数のAPIが同時に401になったとき、それぞれが個別にリフレッシュすると
   * ローテーションにより2回目以降が「使用済みトークンの再提示」になり、バックエンドに
   * 盗用と判定されてファミリー全体が失効する（＝たまに勝手にログアウトする）。
   * refreshOnce の共有により、POST /auth/refresh がちょうど1回だけ呼ばれることを確認する。
   * fake timers は使わず、Promise の解決順序だけで同時性を再現する。
   */
  it("#412 2本のリクエストが同時に401でも、refreshはちょうど1回だけ呼ばれる", async () => {
    const { client, tokenStorage } = await freshClient();
    tokenStorage.saveTokens("old-access", "old-refresh");

    // /posts と /users への「最初の1回」だけ401にし、以降（再試行）は成功させる。
    // /auth/refresh は常に成功させ、呼ばれた回数だけを数える。
    let refreshCallCount = 0;
    const seenOnce = new Set<string>();
    const fetchMock = vi.fn().mockImplementation((url: string) => {
      if (typeof url === "string" && url.includes("/auth/refresh")) {
        refreshCallCount++;
        return Promise.resolve(
          jsonResponse(200, { accessToken: "new-access", refreshToken: "new-refresh", expiresIn: 900 }),
        );
      }
      if (!seenOnce.has(url)) {
        seenOnce.add(url);
        return Promise.resolve(jsonResponse(401, { code: "UNAUTHENTICATED" }));
      }
      return Promise.resolve(jsonResponse(200, { ok: true, url }));
    });
    vi.stubGlobal("fetch", fetchMock);

    const [result1, result2] = await Promise.all([client.request("/posts"), client.request("/users")]);

    expect(result1).toEqual({ ok: true, url: "http://localhost:8080/api/v1/posts" });
    expect(result2).toEqual({ ok: true, url: "http://localhost:8080/api/v1/users" });
    expect(refreshCallCount).toBe(1);
  });
});
