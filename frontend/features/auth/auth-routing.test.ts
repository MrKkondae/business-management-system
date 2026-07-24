import { describe, expect, it } from "vitest";
import {
  firstImplementedMenu,
  resolvePostLoginPath,
} from "./auth-routing";
import type { CurrentUserResponse } from "@/shared/types/auth";

const session: CurrentUserResponse = {
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

describe("auth routing", () => {
  it("제한 세션을 최초 등록 화면으로 보낸다", () => {
    expect(
      resolvePostLoginPath({ ...session, passwordChangeRequired: true }),
    ).toBe("/account/initial-registration");
  });

  it("구현된 업무 메뉴가 없으면 공통 진입점으로 보낸다", () => {
    expect(resolvePostLoginPath(session)).toBe("/");
  });

  it("미구현 메뉴를 선택하지 않는다", () => {
    expect(
      firstImplementedMenu([
        {
          menuId: "01MENU",
          parentMenuId: null,
          menuName: "사용자관리",
          menuUrl: "/system/users",
          sortOrder: 10,
        },
      ]),
    ).toBeNull();
  });
});
