import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "@/shared/api/api-client";
import { InitialRegistrationForm } from "./InitialRegistrationForm";

const mocks = vi.hoisted(() => ({
  completeInitialRegistration: vi.fn(),
  refresh: vi.fn(),
  clear: vi.fn(),
  replace: vi.fn(),
  session: {
    user: {
      userId: "01TEST",
      loginId: "root.admin",
      displayName: "관리자",
    },
    roles: [],
    menus: [],
    passwordChangeRequired: true,
    idleTimeoutSeconds: 600,
    absoluteSessionExpiresAt: "2026-07-24T12:00:00Z",
  },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mocks.replace,
  }),
}));

vi.mock("@/shared/api/users-api", () => ({
  completeInitialRegistration: mocks.completeInitialRegistration,
}));

vi.mock("@/features/auth/LogoutButton", () => ({
  LogoutButton: () => <button type="button">로그아웃</button>,
}));

const promotedSession = {
  ...mocks.session,
  passwordChangeRequired: false,
  idleTimeoutSeconds: 900,
};

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    session: mocks.session,
    refresh: mocks.refresh,
    clear: mocks.clear,
  }),
}));

describe("InitialRegistrationForm", () => {
  beforeEach(() => {
    mocks.completeInitialRegistration.mockReset();
    mocks.refresh.mockReset();
    mocks.clear.mockReset();
    mocks.replace.mockReset();
  });

  it("유효한 입력으로 최초 등록을 완료하고 승격된 세션의 경로로 이동한다", async () => {
    mocks.completeInitialRegistration.mockResolvedValue(undefined);
    mocks.refresh.mockResolvedValue(promotedSession);
    const user = userEvent.setup();
    render(<InitialRegistrationForm />);

    await user.type(
      screen.getByLabelText("새 비밀번호"),
      "Safe!Tiger9#River",
    );
    await user.type(
      screen.getByLabelText("비밀번호 확인"),
      "Safe!Tiger9#River",
    );
    await user.type(
      screen.getByLabelText(/이메일/),
      "  admin@example.com  ",
    );
    await user.type(
      screen.getByLabelText(/휴대전화/),
      "  010-1234-5678  ",
    );
    await user.click(screen.getByRole("button", { name: "등록 완료" }));

    await waitFor(() =>
      expect(mocks.completeInitialRegistration).toHaveBeenCalledWith({
        newPassword: "Safe!Tiger9#River",
        newPasswordConfirmation: "Safe!Tiger9#River",
        emailAddress: "admin@example.com",
        mobileNumber: "010-1234-5678",
      }),
    );
    expect(mocks.refresh).toHaveBeenCalledOnce();
    expect(mocks.replace).toHaveBeenCalledWith("/");
    expect(screen.getByLabelText("새 비밀번호")).toHaveValue("");
    expect(screen.getByLabelText("비밀번호 확인")).toHaveValue("");
  });

  it("비밀번호 정책과 확인값이 맞지 않으면 API를 호출하지 않는다", async () => {
    const user = userEvent.setup();
    render(<InitialRegistrationForm />);

    await user.type(screen.getByLabelText("새 비밀번호"), "Short!1");
    await user.type(screen.getByLabelText("비밀번호 확인"), "Different!9");
    await user.click(screen.getByRole("button", { name: "등록 완료" }));

    expect(mocks.completeInitialRegistration).not.toHaveBeenCalled();
    expect(
      screen.getByText("아래 비밀번호 조건을 모두 충족해 주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("새 비밀번호와 일치하지 않습니다."),
    ).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByLabelText("새 비밀번호")).toHaveFocus(),
    );
  });

  it("401 응답이면 제한 세션을 지우고 로그인 화면으로 이동한다", async () => {
    mocks.completeInitialRegistration.mockRejectedValue(
      new ApiClientError("인증이 필요합니다.", 401),
    );
    const user = userEvent.setup();
    render(<InitialRegistrationForm />);

    await user.type(
      screen.getByLabelText("새 비밀번호"),
      "Safe!Tiger9#River",
    );
    await user.type(
      screen.getByLabelText("비밀번호 확인"),
      "Safe!Tiger9#River",
    );
    await user.click(screen.getByRole("button", { name: "등록 완료" }));

    await waitFor(() => expect(mocks.clear).toHaveBeenCalledOnce());
    expect(mocks.replace).toHaveBeenCalledWith("/login");
    expect(screen.getByLabelText("새 비밀번호")).toHaveValue("");
    expect(screen.getByLabelText("비밀번호 확인")).toHaveValue("");
  });

  it("서버 필드 오류와 추적ID를 화면에 표시한다", async () => {
    mocks.completeInitialRegistration.mockRejectedValue(
      new ApiClientError("입력값을 확인해 주세요.", 400, {
        code: "COMMON_VALIDATION_FAILED",
        message: "입력값을 확인해 주세요.",
        traceId: "trace-registration",
        fieldErrors: [
          {
            field: "emailAddress",
            reason: "FORMAT",
            message: "등록할 수 없는 이메일입니다.",
          },
        ],
      }),
    );
    const user = userEvent.setup();
    render(<InitialRegistrationForm />);

    await user.type(
      screen.getByLabelText("새 비밀번호"),
      "Safe!Tiger9#River",
    );
    await user.type(
      screen.getByLabelText("비밀번호 확인"),
      "Safe!Tiger9#River",
    );
    await user.click(screen.getByRole("button", { name: "등록 완료" }));

    expect(
      await screen.findByText("등록할 수 없는 이메일입니다."),
    ).toBeInTheDocument();
    expect(screen.getByText(/trace-registration/)).toBeInTheDocument();
  });
});
