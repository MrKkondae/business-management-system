import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "./LoginForm";

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  prepareCsrfToken: vi.fn(() => Promise.resolve()),
  establish: vi.fn(),
  replace: vi.fn(),
  consumeSessionNotice: vi.fn(),
  sessionNotice: null as string | null,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mocks.replace,
  }),
}));

vi.mock("@/shared/api/auth-api", () => ({
  login: mocks.login,
  prepareCsrfToken: mocks.prepareCsrfToken,
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    bootstrapError: null,
    sessionNotice: mocks.sessionNotice,
    consumeSessionNotice: mocks.consumeSessionNotice,
    establish: mocks.establish,
  }),
}));

describe("LoginForm", () => {
  beforeEach(() => {
    mocks.login.mockReset();
    mocks.prepareCsrfToken.mockClear();
    mocks.establish.mockReset();
    mocks.replace.mockReset();
    mocks.consumeSessionNotice.mockReset();
    mocks.sessionNotice = null;
  });

  it("필수값 없이 제출하면 API를 호출하지 않고 로그인ID에 포커스한다", async () => {
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(mocks.login).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.getByLabelText("로그인ID")).toHaveFocus(),
    );
    expect(screen.getByText("로그인ID를 입력해 주세요.")).toBeInTheDocument();
  });

  it("제출 중 중복 로그인 요청을 차단한다", async () => {
    let resolveLogin: ((value: unknown) => void) | undefined;
    mocks.login.mockReturnValue(
      new Promise((resolve) => {
        resolveLogin = resolve;
      }),
    );
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("로그인ID"), "root.admin");
    await user.type(screen.getByLabelText("비밀번호"), "Current!Secret9");
    const form = screen.getByRole("button", { name: "로그인" }).closest("form");
    expect(form).not.toBeNull();

    fireEvent.submit(form!);
    fireEvent.submit(form!);

    expect(mocks.login).toHaveBeenCalledTimes(1);

    resolveLogin?.({
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
    });
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/"));
  });

  it("세션 만료 안내를 표시한 뒤 인증 상태에서 소비한다", async () => {
    mocks.sessionNotice = "세션이 만료되었습니다. 다시 로그인해 주세요.";

    render(<LoginForm />);

    expect(
      screen.getByText("세션이 만료되었습니다. 다시 로그인해 주세요."),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(mocks.consumeSessionNotice).toHaveBeenCalledOnce(),
    );
  });
});
