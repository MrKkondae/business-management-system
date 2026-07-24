"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth, type AuthStatus } from "@/features/auth/AuthProvider";

type GateMode = "public-only" | "limited-only" | "authenticated-only";

const allowedStatuses: Record<GateMode, AuthStatus[]> = {
  "public-only": ["unauthenticated", "error"],
  "limited-only": ["limited"],
  "authenticated-only": ["authenticated"],
};

function redirectFor(mode: GateMode, status: AuthStatus): string | null {
  if (status === "loading" || status === "error") {
    return null;
  }
  if (allowedStatuses[mode].includes(status)) {
    return null;
  }
  if (status === "unauthenticated") {
    return "/login";
  }
  if (status === "limited") {
    return "/account/initial-registration";
  }
  return "/";
}

function AuthenticationRecovery() {
  const { bootstrapError, bootstrapTraceId, refresh } = useAuth();
  const [retrying, setRetrying] = useState(false);

  const handleRetry = async () => {
    if (retrying) {
      return;
    }
    setRetrying(true);
    try {
      await refresh();
    } finally {
      setRetrying(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-[var(--canvas)] px-5 py-12">
      <section
        className="w-full max-w-lg border border-[var(--line)] bg-white p-6 sm:p-8"
        aria-labelledby="authentication-recovery-title"
      >
        <p className="text-xs font-bold tracking-[0.16em] text-[var(--focus)] uppercase">
          Connection check
        </p>
        <h1
          id="authentication-recovery-title"
          className="mt-3 text-2xl font-semibold tracking-[-0.03em]"
        >
          로그인 상태를 확인하지 못했습니다
        </h1>
        <div
          className="mt-5 border border-[#efc4be] bg-[var(--danger-surface)] px-4 py-3 text-sm leading-6 text-[var(--danger)]"
          role="alert"
        >
          <p>
            {bootstrapError ??
              "서버 연결을 확인한 뒤 다시 시도해 주세요."}
          </p>
          {bootstrapTraceId ? (
            <p className="mt-1 text-xs opacity-75">
              추적 ID: {bootstrapTraceId}
            </p>
          ) : null}
        </div>
        <button
          type="button"
          onClick={handleRetry}
          disabled={retrying}
          className="mt-6 h-12 w-full bg-[var(--brand)] px-5 text-sm font-bold text-white hover:bg-[var(--brand-strong)] disabled:bg-[#94a29e]"
        >
          {retrying ? "다시 확인하는 중..." : "다시 시도"}
        </button>
      </section>
    </main>
  );
}

export function AuthGate({
  mode,
  children,
}: {
  mode: GateMode;
  children: React.ReactNode;
}) {
  const { status } = useAuth();
  const router = useRouter();
  const redirectPath = redirectFor(mode, status);

  useEffect(() => {
    if (redirectPath) {
      router.replace(redirectPath);
    }
  }, [redirectPath, router]);

  if (status === "loading" || redirectPath) {
    return (
      <div
        className="flex min-h-48 items-center justify-center text-sm text-[var(--muted)]"
        role="status"
      >
        로그인 상태를 확인하고 있습니다.
      </div>
    );
  }

  if (status === "error" && mode !== "public-only") {
    return <AuthenticationRecovery />;
  }

  return children;
}
