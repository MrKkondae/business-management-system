import { describe, expect, it } from "vitest";
import {
  isPasswordPolicySatisfied,
  passwordPolicyChecks,
} from "./password-policy";

const context = {
  loginId: "root.admin",
  displayName: "관리자",
};

describe("passwordPolicyChecks", () => {
  it("정책을 충족하는 비밀번호를 허용한다", () => {
    expect(isPasswordPolicySatisfied("Safe!Tiger9#River", context)).toBe(true);
  });

  it.each([
    ["짧은 비밀번호", "Short!1"],
    ["취약 문자열 포함", "Password!2026Safe"],
    ["로그인ID 포함", "Root.Admin!2026"],
    ["4자 연속 포함", "Safe!abcd9River"],
    ["동일 문자 반복", "Safe!AAAA9River"],
    ["전체 반복 패턴", "Ab1!Ab1!Ab1!"],
  ])("%s를 거부한다", (_name, password) => {
    expect(isPasswordPolicySatisfied(password, context)).toBe(false);
  });

  it("표시할 정책 항목별 결과를 반환한다", () => {
    const checks = passwordPolicyChecks("Safe!Tiger9#River", context);
    expect(checks).toHaveLength(5);
    expect(checks.every((check) => check.passed)).toBe(true);
  });
});
