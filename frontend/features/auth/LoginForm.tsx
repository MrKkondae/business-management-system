"use client";

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
  type KeyboardEvent,
} from "react";
import { useRouter } from "next/navigation";
import { resolvePostLoginPath } from "@/features/auth/auth-routing";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiClientError } from "@/shared/api/api-client";
import { login, prepareCsrfToken } from "@/shared/api/auth-api";
import { FieldError } from "@/shared/ui/FieldError";
import { FormAlert } from "@/shared/ui/FormAlert";

type LoginField = "loginId" | "password";
type FieldErrors = Partial<Record<LoginField, string>>;

function serverFieldErrors(error: ApiClientError): FieldErrors {
  const errors: FieldErrors = {};
  error.problem?.fieldErrors.forEach((fieldError) => {
    if (fieldError.field === "loginId" || fieldError.field === "password") {
      errors[fieldError.field] = fieldError.message;
    }
  });
  return errors;
}

export function LoginForm() {
  const router = useRouter();
  const auth = useAuth();
  const loginIdRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(
    auth.sessionNotice ?? auth.bootstrapError,
  );
  const [formTone, setFormTone] = useState<"error" | "warning">(
    auth.sessionNotice ? "warning" : "error",
  );
  const [traceId, setTraceId] = useState<string | null>(null);
  const [retryUntil, setRetryUntil] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    loginIdRef.current?.focus({ preventScroll: true });
    void prepareCsrfToken().catch(() => {
      setFormError("로그인 보안 요청을 준비하지 못했습니다. 다시 시도해 주세요.");
    });
  }, []);

  useEffect(() => {
    if (auth.sessionNotice) {
      auth.consumeSessionNotice();
    }
  }, [auth]);

  useEffect(() => {
    if (!retryUntil) {
      return;
    }
    const timer = window.setInterval(() => {
      const currentTime = Date.now();
      setNow(currentTime);
      if (currentTime >= retryUntil) {
        window.clearInterval(timer);
        setRetryUntil(null);
        setFormError(null);
      }
    }, 1_000);
    return () => window.clearInterval(timer);
  }, [retryUntil]);

  const retrySeconds = useMemo(() => {
    if (!retryUntil) {
      return 0;
    }
    return Math.max(0, Math.ceil((retryUntil - now) / 1_000));
  }, [now, retryUntil]);

  const validate = (): FieldErrors => {
    const errors: FieldErrors = {};
    const normalizedLoginId = loginId.trim();
    if (!normalizedLoginId) {
      errors.loginId = "로그인ID를 입력해 주세요.";
    } else if (normalizedLoginId.length > 100) {
      errors.loginId = "로그인ID는 100자 이하로 입력해 주세요.";
    }
    if (!password) {
      errors.password = "비밀번호를 입력해 주세요.";
    } else if (password.length > 256) {
      errors.password = "비밀번호는 256자 이하로 입력해 주세요.";
    }
    return errors;
  };

  const focusFirstError = (errors: FieldErrors) => {
    window.requestAnimationFrame(() => {
      if (errors.loginId) {
        loginIdRef.current?.focus();
      } else if (errors.password) {
        passwordRef.current?.focus();
      }
    });
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting || retrySeconds > 0) {
      return;
    }

    const errors = validate();
    setFieldErrors(errors);
    setFormError(null);
    setFormTone("error");
    setTraceId(null);
    if (Object.keys(errors).length > 0) {
      focusFirstError(errors);
      return;
    }

    setSubmitting(true);
    try {
      const session = await login({
        loginId: loginId.trim().toLowerCase(),
        password,
      });
      setPassword("");
      auth.establish(session);
      router.replace(resolvePostLoginPath(session));
    } catch (error) {
      setPassword("");
      if (error instanceof ApiClientError) {
        const nextFieldErrors = serverFieldErrors(error);
        setFieldErrors(nextFieldErrors);
        setTraceId(error.problem?.traceId ?? null);

        if (error.code === "AUTH_LOGIN_FAILED") {
          setFormError("로그인 정보를 확인해 주세요.");
        } else if (error.code === "AUTH_TOO_MANY_ATTEMPTS") {
          const seconds = error.retryAfterSeconds ?? 60;
          setNow(Date.now());
          setRetryUntil(Date.now() + seconds * 1_000);
          setFormError("로그인 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
          setFormTone("warning");
        } else {
          setFormError(error.message);
        }
        focusFirstError(nextFieldErrors);
      } else {
        setFormError("로그인을 처리하지 못했습니다. 다시 시도해 주세요.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handlePasswordKey = (event: KeyboardEvent<HTMLInputElement>) => {
    setCapsLock(event.getModifierState("CapsLock"));
  };

  const disabled = submitting || retrySeconds > 0;

  return (
    <main className="min-w-0 w-full max-w-[420px]" aria-labelledby="login-title">
      <div className="mb-10">
        <p className="mb-3 text-xs font-bold tracking-[0.18em] text-[var(--focus)] uppercase">
          Secure workspace
        </p>
        <h1
          id="login-title"
          className="text-4xl font-semibold tracking-[-0.04em] text-[var(--ink)]"
        >
          다시 만나 반갑습니다
        </h1>
        <p className="mt-4 text-[15px] leading-7 text-[var(--muted)]">
          업무 계정으로 로그인해 오늘의 작업을 이어가세요.
        </p>
      </div>

      <form onSubmit={handleSubmit} noValidate className="space-y-6">
        <FormAlert
          message={
            retrySeconds > 0
              ? `${formError} ${retrySeconds}초 후 다시 시도할 수 있습니다.`
              : formError
          }
          traceId={traceId}
          tone={retrySeconds > 0 ? "warning" : formTone}
        />

        <div>
          <label
            htmlFor="login-id"
            className="mb-2 block text-sm font-semibold text-[var(--ink)]"
          >
            로그인ID
          </label>
          <input
            ref={loginIdRef}
            id="login-id"
            name="loginId"
            type="text"
            value={loginId}
            onChange={(event) => {
              setLoginId(event.target.value);
              setFieldErrors((current) => ({
                ...current,
                loginId: undefined,
              }));
            }}
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            disabled={disabled}
            aria-invalid={Boolean(fieldErrors.loginId)}
            aria-describedby={
              fieldErrors.loginId ? "login-id-error" : undefined
            }
            className="h-13 w-full border border-[var(--line)] bg-white px-4 text-[15px] outline-none transition-colors placeholder:text-[#9ba5a1] hover:border-[#b3bdb8] focus:border-[var(--focus)] disabled:bg-[#f0f2f0]"
            placeholder="예: root.admin"
          />
          <FieldError id="login-id-error">{fieldErrors.loginId}</FieldError>
        </div>

        <div>
          <label
            htmlFor="password"
            className="mb-2 block text-sm font-semibold text-[var(--ink)]"
          >
            비밀번호
          </label>
          <div className="relative">
            <input
              ref={passwordRef}
              id="password"
              name="password"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                setFieldErrors((current) => ({
                  ...current,
                  password: undefined,
                }));
              }}
              onKeyDown={handlePasswordKey}
              onKeyUp={handlePasswordKey}
              autoComplete="current-password"
              disabled={disabled}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={
                fieldErrors.password
                  ? "password-error"
                  : capsLock
                    ? "caps-lock-message"
                    : undefined
              }
              className="h-13 w-full border border-[var(--line)] bg-white px-4 pr-20 text-[15px] outline-none transition-colors hover:border-[#b3bdb8] focus:border-[var(--focus)] disabled:bg-[#f0f2f0]"
            />
            <button
              type="button"
              onClick={() => setShowPassword((current) => !current)}
              disabled={disabled}
              className="absolute inset-y-0 right-0 min-w-16 px-4 text-sm font-semibold text-[var(--focus)] hover:text-[var(--brand-strong)] disabled:text-[#9ba5a1]"
              aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 표시"}
            >
              {showPassword ? "숨김" : "표시"}
            </button>
          </div>
          <FieldError id="password-error">{fieldErrors.password}</FieldError>
          {capsLock ? (
            <p
              id="caps-lock-message"
              className="mt-2 text-sm text-[var(--warning)]"
            >
              Caps Lock이 켜져 있습니다.
            </p>
          ) : null}
        </div>

        <button
          type="submit"
          disabled={disabled}
          className="flex h-14 w-full items-center justify-center bg-[var(--brand)] px-5 text-[15px] font-bold text-white transition-colors hover:bg-[var(--brand-strong)] disabled:cursor-not-allowed disabled:bg-[#94a29e]"
        >
          {submitting
            ? "로그인 중..."
            : retrySeconds > 0
              ? `${retrySeconds}초 후 재시도`
              : "로그인"}
        </button>
      </form>

      <p className="mt-8 text-center text-xs leading-5 text-[#7a8581]">
        계정 사용에 문제가 있으면 시스템 관리자에게 문의하세요.
      </p>
    </main>
  );
}
