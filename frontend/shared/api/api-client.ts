import {
  clearCsrfToken,
  getStoredCsrfToken,
  storeCsrfToken,
} from "@/shared/api/csrf-token-store";
import type { ApiProblem } from "@/shared/types/api-problem";
import type { CsrfTokenResponse } from "@/shared/types/auth";

const API_PREFIX = "/backend-api";

type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  csrf?: boolean;
  retryCsrf?: boolean;
};

export class ApiClientError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly problem: ApiProblem | null = null,
    readonly retryAfterSeconds: number | null = null,
  ) {
    super(message);
    this.name = "ApiClientError";
  }

  get code(): string | null {
    return this.problem?.code ?? null;
  }
}

function isApiProblem(value: unknown): value is ApiProblem {
  if (!value || typeof value !== "object") {
    return false;
  }

  const candidate = value as Partial<ApiProblem>;
  return (
    typeof candidate.code === "string" &&
    typeof candidate.message === "string" &&
    typeof candidate.traceId === "string" &&
    Array.isArray(candidate.fieldErrors)
  );
}

async function readProblem(response: Response): Promise<ApiProblem | null> {
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("json")) {
    return null;
  }

  try {
    const body: unknown = await response.json();
    return isApiProblem(body) ? body : null;
  } catch {
    return null;
  }
}

function retryAfterSeconds(response: Response): number | null {
  if (response.status !== 429) {
    return null;
  }

  const parsed = Number.parseInt(response.headers.get("retry-after") ?? "", 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 60;
}

async function fetchCsrfToken(): Promise<CsrfTokenResponse> {
  const response = await fetch(`${API_PREFIX}/auth/csrf`, {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    const problem = await readProblem(response);
    throw new ApiClientError(
      problem?.message ?? "보안 요청을 준비하지 못했습니다.",
      response.status,
      problem,
    );
  }

  const token = (await response.json()) as CsrfTokenResponse;
  if (!token.headerName || !token.token) {
    throw new ApiClientError("보안 응답 형식이 올바르지 않습니다.", 500);
  }
  storeCsrfToken(token);
  return token;
}

export async function ensureCsrfToken(
  forceRefresh = false,
): Promise<CsrfTokenResponse> {
  if (forceRefresh) {
    clearCsrfToken();
  }

  return getStoredCsrfToken() ?? fetchCsrfToken();
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { body, csrf = false, retryCsrf = true, headers, ...requestInit } =
    options;
  let csrfRetried = false;

  while (true) {
    const requestHeaders = new Headers(headers);
    requestHeaders.set("Accept", "application/json, application/problem+json");

    if (body !== undefined) {
      requestHeaders.set("Content-Type", "application/json");
    }

    if (csrf) {
      const token = await ensureCsrfToken(csrfRetried);
      requestHeaders.set(token.headerName, token.token);
    }

    let response: Response;
    try {
      response = await fetch(`${API_PREFIX}${path}`, {
        ...requestInit,
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: "same-origin",
        cache: "no-store",
        headers: requestHeaders,
      });
    } catch {
      throw new ApiClientError(
        "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        0,
      );
    }

    if (response.ok) {
      if (response.status === 204) {
        return undefined as T;
      }
      return (await response.json()) as T;
    }

    const problem = await readProblem(response);
    if (
      csrf &&
      retryCsrf &&
      !csrfRetried &&
      response.status === 403 &&
      problem?.code === "AUTH_CSRF_INVALID"
    ) {
      csrfRetried = true;
      continue;
    }

    throw new ApiClientError(
      problem?.message ?? "요청을 처리하지 못했습니다.",
      response.status,
      problem,
      retryAfterSeconds(response),
    );
  }
}
