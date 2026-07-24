import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "@/shared/api/api-client";
import type { CurrentUserResponse } from "@/shared/types/auth";
import { AuthProvider, useAuth } from "./AuthProvider";

const mocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
  clearCsrfToken: vi.fn(),
}));

vi.mock("@/shared/api/auth-api", () => ({
  getCurrentUser: mocks.getCurrentUser,
}));

vi.mock("@/shared/api/csrf-token-store", () => ({
  clearCsrfToken: mocks.clearCsrfToken,
}));

const authenticatedSession: CurrentUserResponse = {
  user: {
    userId: "01TEST",
    loginId: "root.admin",
    displayName: "관리자",
  },
  roles: [],
  menus: [],
  passwordChangeRequired: false,
  idleTimeoutSeconds: 900,
  absoluteSessionExpiresAt: "2026-07-24T12:00:00Z",
};

function AuthProbe() {
  const auth = useAuth();
  return (
    <>
      <p data-testid="status">{auth.status}</p>
      <p data-testid="login-id">{auth.session?.user.loginId ?? "none"}</p>
      <p data-testid="error">{auth.bootstrapError ?? "none"}</p>
      <p data-testid="trace-id">{auth.bootstrapTraceId ?? "none"}</p>
      <p data-testid="notice">{auth.sessionNotice ?? "none"}</p>
      <button
        type="button"
        onClick={() =>
          auth.establish({
            ...authenticatedSession,
            passwordChangeRequired: true,
          })
        }
      >
        제한 세션 설정
      </button>
      <button type="button" onClick={auth.clear}>
        세션 삭제
      </button>
      <button type="button" onClick={auth.expire}>
        세션 만료
      </button>
      <button type="button" onClick={auth.consumeSessionNotice}>
        안내 확인
      </button>
    </>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    mocks.getCurrentUser.mockReset();
    mocks.clearCsrfToken.mockReset();
  });

  it("현재 사용자 조회 결과로 일반 인증 상태를 구성한다", async () => {
    mocks.getCurrentUser.mockResolvedValue(authenticatedSession);

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated"),
    );
    expect(screen.getByTestId("login-id")).toHaveTextContent("root.admin");
  });

  it("401 응답이면 CSRF와 세션을 제거하고 미인증 상태로 전환한다", async () => {
    mocks.getCurrentUser.mockRejectedValue(
      new ApiClientError("인증이 필요합니다.", 401),
    );

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"),
    );
    expect(mocks.clearCsrfToken).toHaveBeenCalledOnce();
    expect(screen.getByTestId("login-id")).toHaveTextContent("none");
  });

  it("일시적인 조회 오류는 로그인 화면에서 안내할 수 있도록 보존한다", async () => {
    mocks.getCurrentUser.mockRejectedValue(
      new ApiClientError("서버에 연결할 수 없습니다.", 500, {
        code: "COMMON_INTERNAL_ERROR",
        message: "서버에 연결할 수 없습니다.",
        traceId: "trace-auth",
        fieldErrors: [],
      }),
    );

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("error"),
    );
    expect(screen.getByTestId("error")).toHaveTextContent(
      "서버에 연결할 수 없습니다.",
    );
    expect(screen.getByTestId("trace-id")).toHaveTextContent("trace-auth");
  });

  it("제한 세션 설정과 명시적 세션 삭제를 반영한다", async () => {
    mocks.getCurrentUser.mockResolvedValue(authenticatedSession);
    const user = userEvent.setup();

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated"),
    );

    await user.click(screen.getByRole("button", { name: "제한 세션 설정" }));
    expect(screen.getByTestId("status")).toHaveTextContent("limited");

    await user.click(screen.getByRole("button", { name: "세션 삭제" }));
    expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated");
    expect(mocks.clearCsrfToken).toHaveBeenCalledOnce();
  });

  it("세션 만료 안내를 로그인 화면에서 한 번 소비할 수 있게 유지한다", async () => {
    mocks.getCurrentUser.mockResolvedValue(authenticatedSession);
    const user = userEvent.setup();

    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated"),
    );

    await user.click(screen.getByRole("button", { name: "세션 만료" }));
    expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated");
    expect(screen.getByTestId("notice")).toHaveTextContent(
      "세션이 만료되었습니다.",
    );

    await user.click(screen.getByRole("button", { name: "안내 확인" }));
    expect(screen.getByTestId("notice")).toHaveTextContent("none");
  });
});
