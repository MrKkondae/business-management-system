import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AuthStatus } from "./AuthProvider";
import { AuthGate } from "./AuthGate";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  status: "loading" as AuthStatus,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mocks.replace,
  }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: mocks.status,
  }),
}));

describe("AuthGate", () => {
  beforeEach(() => {
    mocks.replace.mockReset();
    mocks.status = "loading";
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
});
