"use client";

import {
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";
import { useRouter } from "next/navigation";
import {
  isPasswordPolicySatisfied,
  passwordPolicyChecks,
} from "@/features/account/initial-registration/password-policy";
import { resolvePostLoginPath } from "@/features/auth/auth-routing";
import { useAuth } from "@/features/auth/AuthProvider";
import { LogoutButton } from "@/features/auth/LogoutButton";
import { ApiClientError } from "@/shared/api/api-client";
import { completeInitialRegistration } from "@/shared/api/users-api";
import { FieldError } from "@/shared/ui/FieldError";
import { FormAlert } from "@/shared/ui/FormAlert";

type RegistrationField =
  | "newPassword"
  | "newPasswordConfirmation"
  | "emailAddress"
  | "mobileNumber";
type FieldErrors = Partial<Record<RegistrationField, string>>;

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;
const mobilePattern = /^[0-9+() -]*$/u;

function serverFieldErrors(error: ApiClientError): FieldErrors {
  const errors: FieldErrors = {};
  error.problem?.fieldErrors.forEach((fieldError) => {
    if (
      fieldError.field === "newPassword" ||
      fieldError.field === "newPasswordConfirmation" ||
      fieldError.field === "emailAddress" ||
      fieldError.field === "mobileNumber"
    ) {
      errors[fieldError.field] = fieldError.message;
    }
  });
  return errors;
}

export function InitialRegistrationForm() {
  const router = useRouter();
  const auth = useAuth();
  const passwordRef = useRef<HTMLInputElement>(null);
  const confirmationRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const mobileRef = useRef<HTMLInputElement>(null);
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [emailAddress, setEmailAddress] = useState("");
  const [mobileNumber, setMobileNumber] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  const session = auth.session;
  const context = useMemo(
    () => ({
      loginId: session?.user.loginId ?? "",
      displayName: session?.user.displayName ?? "",
    }),
    [session],
  );
  const policyChecks = useMemo(
    () => passwordPolicyChecks(newPassword, context),
    [context, newPassword],
  );

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {};
    if (!newPassword) {
      errors.newPassword = "새 비밀번호를 입력해 주세요.";
    } else if (!isPasswordPolicySatisfied(newPassword, context)) {
      errors.newPassword = "아래 비밀번호 조건을 모두 충족해 주세요.";
    }
    if (!confirmation) {
      errors.newPasswordConfirmation = "비밀번호 확인값을 입력해 주세요.";
    } else if (confirmation !== newPassword) {
      errors.newPasswordConfirmation = "새 비밀번호와 일치하지 않습니다.";
    }

    const normalizedEmail = emailAddress.trim();
    if (normalizedEmail.length > 100) {
      errors.emailAddress = "이메일은 100자 이하로 입력해 주세요.";
    } else if (normalizedEmail && !emailPattern.test(normalizedEmail)) {
      errors.emailAddress = "이메일 형식을 확인해 주세요.";
    }

    const normalizedMobile = mobileNumber.trim();
    if (normalizedMobile.length > 20) {
      errors.mobileNumber = "휴대전화 번호는 20자 이하로 입력해 주세요.";
    } else if (normalizedMobile && !mobilePattern.test(normalizedMobile)) {
      errors.mobileNumber = "숫자와 +, 괄호, 하이픈만 입력할 수 있습니다.";
    }
    return errors;
  };

  const focusFirstError = (errors: FieldErrors) => {
    window.requestAnimationFrame(() => {
      if (errors.newPassword) {
        passwordRef.current?.focus();
      } else if (errors.newPasswordConfirmation) {
        confirmationRef.current?.focus();
      } else if (errors.emailAddress) {
        emailRef.current?.focus();
      } else if (errors.mobileNumber) {
        mobileRef.current?.focus();
      }
    });
  };

  const clearSensitiveFields = () => {
    setNewPassword("");
    setConfirmation("");
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) {
      return;
    }

    const errors = validate();
    setFieldErrors(errors);
    setFormError(null);
    setTraceId(null);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      await completeInitialRegistration({
        newPassword,
        newPasswordConfirmation: confirmation,
        emailAddress: emailAddress.trim() || null,
        mobileNumber: mobileNumber.trim() || null,
      });
      clearSensitiveFields();
      const promotedSession = await auth.refresh();
      if (promotedSession) {
        router.replace(resolvePostLoginPath(promotedSession));
      }
    } catch (error) {
      clearSensitiveFields();
      if (error instanceof ApiClientError) {
        if (error.status === 401) {
          auth.clear();
          router.replace("/login");
          return;
        }

        const nextFieldErrors = serverFieldErrors(error);
        setFieldErrors(nextFieldErrors);
        setTraceId(error.problem?.traceId ?? null);
        setFormError(
          error.code === "COMMON_INVALID_STATE"
            ? "계정 상태가 변경되었습니다. 로그인 상태를 다시 확인해 주세요."
            : error.message,
        );
        focusFirstError(nextFieldErrors);

        if (error.code === "COMMON_INVALID_STATE") {
          await auth.refresh();
        }
      } else {
        setFormError("계정 설정을 완료하지 못했습니다. 다시 시도해 주세요.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const expiresAt = session?.absoluteSessionExpiresAt
    ? new Intl.DateTimeFormat("ko-KR", {
        hour: "2-digit",
        minute: "2-digit",
      }).format(new Date(session.absoluteSessionExpiresAt))
    : null;

  return (
    <main
      className="min-w-0 w-full max-w-[620px]"
      aria-labelledby="registration-title"
    >
      <div className="mb-7 flex items-start justify-between gap-4">
        <div>
          <p className="mb-2 text-xs font-bold tracking-[0.16em] text-[var(--focus)] uppercase">
            First-time setup
          </p>
          <h1
            id="registration-title"
            className="text-3xl font-semibold tracking-[-0.04em] text-[var(--ink)] sm:text-4xl"
          >
            계정 설정을 완료해 주세요
          </h1>
          <p className="mt-3 text-sm leading-6 text-[var(--muted)]">
            {session?.user.displayName}님, 새 비밀번호를 등록하면 업무 메뉴를
            사용할 수 있습니다.
          </p>
          {expiresAt ? (
            <p className="mt-1 text-xs text-[#7b8783]">
              현재 설정 세션은 {expiresAt}까지 유효합니다.
            </p>
          ) : null}
        </div>
        <LogoutButton compact />
      </div>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="border border-[var(--line)] bg-white p-5 sm:p-8"
      >
        <div className="mb-6">
          <FormAlert message={formError} traceId={traceId} />
        </div>

        <div className="grid gap-6 sm:grid-cols-2">
          <div>
            <label
              htmlFor="new-password"
              className="mb-2 block text-sm font-semibold"
            >
              새 비밀번호
            </label>
            <div className="relative">
              <input
                ref={passwordRef}
                id="new-password"
                name="newPassword"
                type={showPassword ? "text" : "password"}
                value={newPassword}
                onChange={(event) => {
                  setNewPassword(event.target.value);
                  setFieldErrors((current) => ({
                    ...current,
                    newPassword: undefined,
                  }));
                }}
                autoComplete="new-password"
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.newPassword)}
                aria-describedby="new-password-error password-policy"
                className="h-13 w-full border border-[var(--line)] px-4 pr-18 outline-none hover:border-[#b3bdb8] focus:border-[var(--focus)] disabled:bg-[#f0f2f0]"
              />
              <button
                type="button"
                onClick={() => setShowPassword((current) => !current)}
                className="absolute inset-y-0 right-0 min-w-16 px-3 text-sm font-semibold text-[var(--focus)]"
                aria-label={showPassword ? "새 비밀번호 숨기기" : "새 비밀번호 표시"}
                disabled={submitting}
              >
                {showPassword ? "숨김" : "표시"}
              </button>
            </div>
            <FieldError id="new-password-error">
              {fieldErrors.newPassword}
            </FieldError>
          </div>

          <div>
            <label
              htmlFor="new-password-confirmation"
              className="mb-2 block text-sm font-semibold"
            >
              비밀번호 확인
            </label>
            <div className="relative">
              <input
                ref={confirmationRef}
                id="new-password-confirmation"
                name="newPasswordConfirmation"
                type={showConfirmation ? "text" : "password"}
                value={confirmation}
                onChange={(event) => {
                  setConfirmation(event.target.value);
                  setFieldErrors((current) => ({
                    ...current,
                    newPasswordConfirmation: undefined,
                  }));
                }}
                autoComplete="new-password"
                disabled={submitting}
                aria-invalid={Boolean(fieldErrors.newPasswordConfirmation)}
                aria-describedby="new-password-confirmation-error"
                className="h-13 w-full border border-[var(--line)] px-4 pr-18 outline-none hover:border-[#b3bdb8] focus:border-[var(--focus)] disabled:bg-[#f0f2f0]"
              />
              <button
                type="button"
                onClick={() => setShowConfirmation((current) => !current)}
                className="absolute inset-y-0 right-0 min-w-16 px-3 text-sm font-semibold text-[var(--focus)]"
                aria-label={
                  showConfirmation
                    ? "비밀번호 확인값 숨기기"
                    : "비밀번호 확인값 표시"
                }
                disabled={submitting}
              >
                {showConfirmation ? "숨김" : "표시"}
              </button>
            </div>
            <FieldError id="new-password-confirmation-error">
              {fieldErrors.newPasswordConfirmation}
            </FieldError>
          </div>
        </div>

        <div
          id="password-policy"
          className="mt-5 border border-[var(--line)] bg-[var(--surface-subtle)] p-4"
        >
          <p className="mb-3 text-sm font-semibold">비밀번호 조건</p>
          <ul className="grid gap-2 text-xs sm:grid-cols-2">
            {policyChecks.map((check) => (
              <li
                key={check.id}
                className={
                  check.passed
                    ? "text-[var(--focus)]"
                    : "text-[var(--muted)]"
                }
              >
                <span className="mr-2 font-bold" aria-hidden="true">
                  {check.passed ? "✓" : "·"}
                </span>
                {check.label}
              </li>
            ))}
          </ul>
        </div>

        <div className="mt-7 grid gap-6 sm:grid-cols-2">
          <div>
            <label
              htmlFor="email-address"
              className="mb-2 block text-sm font-semibold"
            >
              이메일 <span className="font-normal text-[var(--muted)]">(선택)</span>
            </label>
            <input
              ref={emailRef}
              id="email-address"
              name="emailAddress"
              type="email"
              value={emailAddress}
              onChange={(event) => {
                setEmailAddress(event.target.value);
                setFieldErrors((current) => ({
                  ...current,
                  emailAddress: undefined,
                }));
              }}
              autoComplete="email"
              disabled={submitting}
              aria-invalid={Boolean(fieldErrors.emailAddress)}
              aria-describedby={
                fieldErrors.emailAddress ? "email-address-error" : undefined
              }
              className="h-13 w-full border border-[var(--line)] px-4 outline-none hover:border-[#b3bdb8] focus:border-[var(--focus)] disabled:bg-[#f0f2f0]"
              placeholder="name@company.com"
            />
            <FieldError id="email-address-error">
              {fieldErrors.emailAddress}
            </FieldError>
          </div>

          <div>
            <label
              htmlFor="mobile-number"
              className="mb-2 block text-sm font-semibold"
            >
              휴대전화 <span className="font-normal text-[var(--muted)]">(선택)</span>
            </label>
            <input
              ref={mobileRef}
              id="mobile-number"
              name="mobileNumber"
              type="tel"
              value={mobileNumber}
              onChange={(event) => {
                setMobileNumber(event.target.value);
                setFieldErrors((current) => ({
                  ...current,
                  mobileNumber: undefined,
                }));
              }}
              autoComplete="tel"
              disabled={submitting}
              aria-invalid={Boolean(fieldErrors.mobileNumber)}
              aria-describedby={
                fieldErrors.mobileNumber ? "mobile-number-error" : undefined
              }
              className="h-13 w-full border border-[var(--line)] px-4 outline-none hover:border-[#b3bdb8] focus:border-[var(--focus)] disabled:bg-[#f0f2f0]"
              placeholder="010-0000-0000"
            />
            <FieldError id="mobile-number-error">
              {fieldErrors.mobileNumber}
            </FieldError>
          </div>
        </div>

        <div className="mt-8 flex flex-col-reverse items-stretch justify-between gap-3 border-t border-[var(--line)] pt-6 sm:flex-row sm:items-center">
          <p className="text-xs leading-5 text-[var(--muted)]">
            등록 완료 후 새 비밀번호로 다시 로그인할 수 있습니다.
          </p>
          <button
            type="submit"
            disabled={submitting}
            className="h-13 min-w-40 bg-[var(--brand)] px-6 text-sm font-bold text-white hover:bg-[var(--brand-strong)] disabled:bg-[#94a29e]"
          >
            {submitting ? "등록 중..." : "등록 완료"}
          </button>
        </div>
      </form>
    </main>
  );
}
