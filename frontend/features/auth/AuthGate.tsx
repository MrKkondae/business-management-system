"use client";

import { useEffect } from "react";
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

  return children;
}
