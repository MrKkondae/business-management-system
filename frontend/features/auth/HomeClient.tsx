"use client";

import { AuthGate } from "@/features/auth/AuthGate";
import { useAuth } from "@/features/auth/AuthProvider";
import { LogoutButton } from "@/features/auth/LogoutButton";

function AuthenticatedHome() {
  const { session } = useAuth();
  if (!session) {
    return null;
  }

  const roleNames =
    session.roles.map((role) => role.roleName).join(", ") || "없음";
  const idleTimeoutMinutes = Math.floor(session.idleTimeoutSeconds / 60);

  return (
    <main className="min-h-screen bg-[var(--canvas)]">
      <header
        className="border-b border-[var(--line)] bg-white"
        aria-label="공통 애플리케이션 헤더"
      >
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
          <div className="flex min-w-0 items-center gap-3 sm:gap-4">
            <div className="min-w-0 text-right">
              <p className="max-w-28 truncate text-sm font-semibold sm:max-w-48">
                {session.user.displayName}
              </p>
              <p className="hidden max-w-48 truncate text-xs text-[var(--muted)] sm:block">
                {session.user.loginId}
              </p>
            </div>
            <LogoutButton compact />
          </div>
        </div>
      </header>

      <section
        className="mx-auto max-w-6xl px-5 py-14 sm:px-8 sm:py-20"
        aria-labelledby="workspace-title"
      >
        <p className="text-xs font-bold tracking-[0.16em] text-[var(--focus)] uppercase">
          Workspace ready
        </p>
        <h1
          id="workspace-title"
          className="mt-3 max-w-2xl text-4xl font-semibold tracking-[-0.04em] sm:text-5xl"
        >
          로그인되었습니다.
        </h1>
        <p className="mt-5 max-w-xl text-base leading-7 text-[var(--muted)]">
          인증과 공통 애플리케이션 연결이 완료되었습니다. 현재 구현되어 사용할
          수 있는 업무 메뉴가 없습니다.
        </p>

        <dl className="mt-10 grid gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-3">
          <div className="bg-white p-6">
            <dt className="text-xs font-semibold text-[var(--muted)]">사용자</dt>
            <dd className="mt-2 break-words text-lg font-bold">
              {session.user.displayName}
            </dd>
          </div>
          <div className="bg-white p-6">
            <dt className="text-xs font-semibold text-[var(--muted)]">역할</dt>
            <dd className="mt-2 break-words text-lg font-bold">{roleNames}</dd>
          </div>
          <div className="bg-white p-6">
            <dt className="text-xs font-semibold text-[var(--muted)]">
              세션 유휴시간
            </dt>
            <dd className="mt-2 text-lg font-bold">{idleTimeoutMinutes}분</dd>
          </div>
        </dl>

        <aside className="mt-8 border-l-4 border-[var(--accent)] bg-white px-6 py-5 text-sm leading-6 text-[var(--muted)]">
          사용자관리 등 업무 화면이 구현되면 권한이 있는 첫 메뉴로 자동
          이동합니다. 이 영역은 대시보드 데이터를 조회하지 않습니다.
        </aside>
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
