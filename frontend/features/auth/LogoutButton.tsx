"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/AuthProvider";
import { logout } from "@/shared/api/auth-api";

export function LogoutButton({ compact = false }: { compact?: boolean }) {
  const router = useRouter();
  const auth = useAuth();
  const [pending, setPending] = useState(false);

  const handleLogout = async () => {
    if (pending) {
      return;
    }
    setPending(true);
    try {
      await logout();
    } finally {
      auth.clear();
      router.replace("/login");
      setPending(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleLogout}
      disabled={pending}
      className={
        compact
          ? "min-h-10 border border-[var(--line)] bg-white px-4 text-sm font-semibold text-[var(--ink)] hover:border-[#aeb9b4] disabled:text-[var(--muted)]"
          : "h-11 border border-[#385551] px-5 text-sm font-semibold text-white hover:bg-[#244844] disabled:text-[#91a7a3]"
      }
    >
      {pending ? "로그아웃 중..." : "로그아웃"}
    </button>
  );
}
