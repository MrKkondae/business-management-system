import { fireEvent, render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiClientError } from "@/shared/api/api-client";
import { SessionActivityTracker } from "./SessionActivityTracker";

const mocks = vi.hoisted(() => ({
  recordUserActivity: vi.fn(),
  clear: vi.fn(),
  replace: vi.fn(),
  status: "authenticated",
  pathname: "/",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mocks.pathname,
  useRouter: () => ({
    replace: mocks.replace,
  }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({
    status: mocks.status,
    clear: mocks.clear,
  }),
}));

vi.mock("@/shared/api/auth-api", () => ({
  recordUserActivity: mocks.recordUserActivity,
}));

describe("SessionActivityTracker", () => {
  beforeEach(() => {
    mocks.recordUserActivity.mockReset();
    mocks.recordUserActivity.mockResolvedValue(undefined);
    mocks.clear.mockReset();
    mocks.replace.mockReset();
    mocks.status = "authenticated";
    mocks.pathname = "/";
    vi.spyOn(Date, "now").mockReturnValue(100_000);
  });

  it("사용자 활동을 최대 1분에 한 번만 서버에 기록한다", async () => {
    render(<SessionActivityTracker />);

    fireEvent.pointerDown(window);
    await waitFor(() =>
      expect(mocks.recordUserActivity).toHaveBeenCalledOnce(),
    );

    vi.mocked(Date.now).mockReturnValue(100_001);
    fireEvent.keyDown(window);
    expect(mocks.recordUserActivity).toHaveBeenCalledOnce();

    vi.mocked(Date.now).mockReturnValue(160_001);
    fireEvent.scroll(window);
    await waitFor(() =>
      expect(mocks.recordUserActivity).toHaveBeenCalledTimes(2),
    );
  });

  it("활동 갱신에서 401을 받으면 세션을 지우고 로그인 화면으로 이동한다", async () => {
    mocks.recordUserActivity.mockRejectedValue(
      new ApiClientError("인증이 필요합니다.", 401),
    );
    render(<SessionActivityTracker />);

    fireEvent.pointerDown(window);

    await waitFor(() => expect(mocks.clear).toHaveBeenCalledOnce());
    expect(mocks.replace).toHaveBeenCalledWith("/login");
  });

  it("미인증 상태에서는 사용자 이벤트를 전송하지 않는다", () => {
    mocks.status = "unauthenticated";
    render(<SessionActivityTracker />);

    fireEvent.pointerDown(window);

    expect(mocks.recordUserActivity).not.toHaveBeenCalled();
  });
});
