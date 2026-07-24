import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LogoutButton } from "./LogoutButton";

const mocks = vi.hoisted(() => ({
  logout: vi.fn(),
  clear: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mocks.replace,
  }),
}));

vi.mock("@/shared/api/auth-api", () => ({
  logout: mocks.logout,
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    clear: mocks.clear,
  }),
}));

describe("LogoutButton", () => {
  beforeEach(() => {
    mocks.logout.mockReset();
    mocks.clear.mockReset();
    mocks.replace.mockReset();
  });

  it("로그아웃 성공 후 클라이언트 세션을 지우고 로그인 화면으로 이동한다", async () => {
    mocks.logout.mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<LogoutButton />);

    await user.click(screen.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledOnce());
    expect(mocks.clear).toHaveBeenCalledOnce();
    expect(mocks.replace).toHaveBeenCalledWith("/login");
  });

  it("서버 로그아웃 요청이 실패해도 로컬 세션을 정리한다", async () => {
    mocks.logout.mockRejectedValue(new Error("network"));
    const user = userEvent.setup();
    render(<LogoutButton />);

    await user.click(screen.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(mocks.clear).toHaveBeenCalledOnce());
    expect(mocks.replace).toHaveBeenCalledWith("/login");
  });

  it("처리 중에는 버튼을 비활성화해 중복 요청을 막는다", async () => {
    let completeLogout: (() => void) | undefined;
    mocks.logout.mockReturnValue(
      new Promise<void>((resolve) => {
        completeLogout = resolve;
      }),
    );
    const user = userEvent.setup();
    render(<LogoutButton />);

    await user.click(screen.getByRole("button", { name: "로그아웃" }));

    expect(screen.getByRole("button", { name: "로그아웃 중..." })).toBeDisabled();
    expect(mocks.logout).toHaveBeenCalledOnce();
    completeLogout?.();
    await waitFor(() => expect(mocks.clear).toHaveBeenCalledOnce());
  });
});
