"use client";

import { AuthGate } from "@/features/auth/AuthGate";
import { useAuth } from "@/features/auth/AuthProvider";
import { LogoutButton } from "@/features/auth/LogoutButton";

function AuthenticatedHome() {
  const { session } = useAuth();
  if (!session) {
    return null;
  }

  return (
    <main className="min-h-screen bg-[var(--canvas)]">
      <header className="border-b border-[var(--line)] bg-white">
        <div className="mx-auto flex min-h-18 max-w-6xl items-center justify-between px-5 sm:px-8">
          <div className="flex items-center gap-3">
            <span className="flex size-9 items-center justify-center bg-[var(--brand)] text-sm font-black text-[var(--accent)]">
              B
            </span>
            <div>
              <p className="text-sm font-bold tracking-[0.08em]">BMS</p>
              <p className="text-xs text-[var(--muted)]">Business workspace</p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-semibold">{session.user.displayName}</p>
              <p className="text-xs text-[var(--muted)]">
                {session.user.loginId}
              </p>
            </div>
            <LogoutButton compact />
          </div>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-5 py-14 sm:px-8 sm:py-20">
        <p className="text-xs font-bold tracking-[0.16em] text-[var(--focus)] uppercase">
          Workspace ready
        </p>
        <h1 className="mt-3 max-w-2xl text-4xl font-semibold tracking-[-0.04em] sm:text-5xl">
          로그인되었습니다.
        </h1>
        <p className="mt-5 max-w-xl text-base leading-7 text-[var(--muted)]">
          인증과 공통 애플리케이션 연결이 완료되었습니다. 현재 구현되어 사용할
          수 있는 업무 메뉴가 없습니다.
        </p>

        <div className="mt-10 grid gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-3">
          <div className="bg-white p-6">
            <p className="text-xs font-semibold text-[var(--muted)]">사용자</p>
            <p className="mt-2 text-lg font-bold">{session.user.displayName}</p>
          </div>
          <div className="bg-white p-6">
            <p className="text-xs font-semibold text-[var(--muted)]">역할</p>
            <p className="mt-2 text-lg font-bold">
              {session.roles.map((role) => role.roleName).join(", ") || "없음"}
            </p>
          </div>
          <div className="bg-white p-6">
            <p className="text-xs font-semibold text-[var(--muted)]">
              세션 유휴시간
            </p>
            <p className="mt-2 text-lg font-bold">
              {Math.floor(session.idleTimeoutSeconds / 60)}분
            </p>
          </div>
        </div>

        <div className="mt-8 border-l-4 border-[var(--accent)] bg-white px-6 py-5 text-sm leading-6 text-[var(--muted)]">
          사용자관리 등 업무 화면이 구현되면 권한이 있는 첫 메뉴로 자동
          이동합니다. 이 영역은 대시보드 데이터를 조회하지 않습니다.
        </div>
      </section>
    </main>
  );
}

export function HomeClient() {
  return (
    <AuthGate mode="authenticated-only">
      <AuthenticatedHome />
    </AuthGate>
  );
}
