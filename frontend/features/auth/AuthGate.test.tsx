import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthStatus } from "./AuthProvider";
import { AuthGate } from "./AuthGate";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  refresh: vi.fn(),
  status: "loading" as AuthStatus,
  bootstrapError: null as string | null,
  bootstrapTraceId: null as string | null,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mocks.replace,
  }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: mocks.status,
    bootstrapError: mocks.bootstrapError,
    bootstrapTraceId: mocks.bootstrapTraceId,
    refresh: mocks.refresh,
  }),
}));

describe("AuthGate", () => {
  beforeEach(() => {
    mocks.replace.mockReset();
    mocks.refresh.mockReset();
    mocks.status = "loading";
    mocks.bootstrapError = null;
    mocks.bootstrapTraceId = null;
  });

  it("허용된 인증 상태에서는 자식 화면을 표시한다", () => {
    mocks.status = "authenticated";

    render(
      <AuthGate mode="authenticated-only">
        <p>보호 화면</p>
      </AuthGate>,
    );

    expect(screen.getByText("보호 화면")).toBeInTheDocument();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it.each([
    ["미인증 사용자의 보호 화면", "unauthenticated", "authenticated-only", "/login"],
    ["제한 세션의 일반 화면", "limited", "authenticated-only", "/account/initial-registration"],
    ["일반 세션의 공개 화면", "authenticated", "public-only", "/"],
  ] as const)(
    "%s 접근을 올바른 경로로 전환한다",
    async (_name, status, mode, expectedPath) => {
      mocks.status = status;

      render(
        <AuthGate mode={mode}>
          <p>차단 화면</p>
        </AuthGate>,
      );

      await waitFor(() =>
        expect(mocks.replace).toHaveBeenCalledWith(expectedPath),
      );
      expect(screen.queryByText("차단 화면")).not.toBeInTheDocument();
      expect(screen.getByRole("status")).toHaveTextContent(
        "로그인 상태를 확인하고 있습니다.",
      );
    },
  );

  it("보호 화면의 세션 확인 오류에서 추적ID를 표시하고 재시도한다", async () => {
    mocks.status = "error";
    mocks.bootstrapError = "서버에 연결할 수 없습니다.";
    mocks.bootstrapTraceId = "trace-auth";
    mocks.refresh.mockResolvedValue(null);
    const user = userEvent.setup();

    render(
      <AuthGate mode="authenticated-only">
        <p>보호 화면</p>
      </AuthGate>,
    );

    expect(
      screen.getByRole("heading", {
        name: "로그인 상태를 확인하지 못했습니다",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText(/trace-auth/)).toBeInTheDocument();
    expect(screen.queryByText("보호 화면")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(mocks.refresh).toHaveBeenCalledOnce();
  });
});
