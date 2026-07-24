import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { HomeClient } from "./HomeClient";

const mocks = vi.hoisted(() => ({
  session: {
    user: {
      userId: "01TEST",
      loginId: "root.admin",
      displayName: "관리자",
    },
    roles: [
      {
        roleId: "01ROLE",
        roleName: "시스템관리자",
      },
      {
        roleId: "02ROLE",
        roleName: "일반사용자",
      },
    ],
    menus: [],
    passwordChangeRequired: false,
    idleTimeoutSeconds: 900,
    absoluteSessionExpiresAt: "2026-07-24T20:00:00Z",
  },
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    session: mocks.session,
  }),
}));

vi.mock("@/features/auth/AuthGate", () => ({
  AuthGate: ({ children }: { children: React.ReactNode }) => children,
}));

vi.mock("@/features/auth/LogoutButton", () => ({
  LogoutButton: () => <button type="button">로그아웃</button>,
}));

describe("HomeClient", () => {
  it("일반 세션 사용자·역할·유휴시간과 메뉴 없음 안내를 표시한다", () => {
    render(<HomeClient />);

    expect(
      screen.getByRole("heading", { name: "로그인되었습니다." }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("관리자")).toHaveLength(2);
    expect(screen.getByText("root.admin")).toBeInTheDocument();
    expect(
      screen.getByText("시스템관리자, 일반사용자"),
    ).toBeInTheDocument();
    expect(screen.getByText("15분")).toBeInTheDocument();
    expect(
      screen.getByText(/현재 구현되어 사용할 수 있는 업무 메뉴가 없습니다/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
  });
});
