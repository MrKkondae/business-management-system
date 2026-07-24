import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./api-client";
import { clearCsrfToken } from "./csrf-token-store";

const problem = {
  code: "AUTH_CSRF_INVALID",
  message: "요청을 다시 시도해 주세요.",
  traceId: "trace-1",
  fieldErrors: [],
};

describe("apiRequest", () => {
  beforeEach(() => {
    clearCsrfToken();
    vi.unstubAllGlobals();
  });

  it("CSRF 오류 시 토큰을 갱신하고 상태 변경 요청을 한 번 재시도한다", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            headerName: "X-CSRF-TOKEN",
            token: "old-token",
          }),
          {
            status: 200,
            headers: { "content-type": "application/json" },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify(problem), {
          status: 403,
          headers: { "content-type": "application/problem+json" },
        }),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            headerName: "X-CSRF-TOKEN",
            token: "new-token",
          }),
          {
            status: 200,
            headers: { "content-type": "application/json" },
          },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await apiRequest<void>("/auth/logout", {
      method: "POST",
      csrf: true,
    });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    const firstMutationHeaders = fetchMock.mock.calls[1]?.[1]?.headers;
    const retriedMutationHeaders = fetchMock.mock.calls[3]?.[1]?.headers;
    expect(new Headers(firstMutationHeaders).get("X-CSRF-TOKEN")).toBe(
      "old-token",
    );
    expect(new Headers(retriedMutationHeaders).get("X-CSRF-TOKEN")).toBe(
      "new-token",
    );
  });

  it("429 Retry-After가 없으면 60초를 사용한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(
          JSON.stringify({
            ...problem,
            code: "AUTH_TOO_MANY_ATTEMPTS",
          }),
          {
            status: 429,
            headers: { "content-type": "application/problem+json" },
          },
        ),
      ),
    );

    await expect(apiRequest("/auth/login")).rejects.toMatchObject({
      retryAfterSeconds: 60,
    });
  });

  it("429 Retry-After 헤더의 대기시간을 전달한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(
          JSON.stringify({
            ...problem,
            code: "AUTH_TOO_MANY_ATTEMPTS",
          }),
          {
            status: 429,
            headers: {
              "content-type": "application/problem+json",
              "retry-after": "17",
            },
          },
        ),
      ),
    );

    await expect(apiRequest("/auth/login")).rejects.toMatchObject({
      status: 429,
      retryAfterSeconds: 17,
    });
  });

  it.each([
    [401, "AUTH_AUTHENTICATION_REQUIRED"],
    [403, "AUTH_ACCESS_DENIED"],
  ])("%i 오류의 상태와 Problem 정보를 보존한다", async (status, code) => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(
          JSON.stringify({
            ...problem,
            code,
          }),
          {
            status,
            headers: { "content-type": "application/problem+json" },
          },
        ),
      ),
    );

    await expect(apiRequest("/protected")).rejects.toMatchObject({
      status,
      problem: {
        code,
        traceId: "trace-1",
      },
    });
  });
});
