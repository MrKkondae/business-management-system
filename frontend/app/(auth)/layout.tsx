export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="grid min-h-screen grid-cols-[minmax(0,1fr)] lg:grid-cols-[minmax(340px,0.9fr)_minmax(520px,1.1fr)]">
      <aside className="relative hidden overflow-hidden bg-[var(--brand)] p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="relative z-10">
          <div className="flex items-center gap-3">
            <span className="flex size-11 items-center justify-center border border-[#47635f] bg-[var(--brand-strong)] text-base font-black text-[var(--accent)]">
              B
            </span>
            <div>
              <p className="text-sm font-bold tracking-[0.18em]">BMS</p>
              <p className="mt-0.5 text-xs text-[#a9bbb7]">
                Business Management System
              </p>
            </div>
          </div>

          <div className="mt-28 max-w-md">
            <p className="text-xs font-bold tracking-[0.2em] text-[var(--accent)] uppercase">
              One connected workspace
            </p>
            <p className="mt-5 text-5xl leading-[1.15] font-semibold tracking-[-0.045em]">
              업무를 연결하고
              <br />
              운영을 단순하게
            </p>
            <p className="mt-6 max-w-sm text-[15px] leading-7 text-[#b9c8c5]">
              고객, 영업, 프로젝트와 인력 정보를 하나의 기준으로 관리하는
              비즈니스 워크스페이스입니다.
            </p>
          </div>
        </div>

        <div className="relative z-10 grid grid-cols-3 border-y border-[#385551] py-5 text-xs text-[#9db0ac]">
          <span>Secure session</span>
          <span className="text-center">Role based</span>
          <span className="text-right">Audit ready</span>
        </div>

        <div
          className="absolute -right-28 bottom-24 size-72 border-[72px] border-[#214742]"
          aria-hidden="true"
        />
        <div
          className="absolute right-12 bottom-40 size-24 bg-[var(--accent)] opacity-90"
          aria-hidden="true"
        />
      </aside>

      <section className="flex min-h-screen min-w-0 flex-col bg-[var(--canvas)]">
        <header className="flex h-18 items-center border-b border-[var(--line)] px-5 lg:hidden">
          <span className="flex size-9 items-center justify-center bg-[var(--brand)] text-sm font-black text-[var(--accent)]">
            B
          </span>
          <p className="ml-3 text-sm font-bold tracking-[0.12em]">BMS</p>
        </header>
        <div className="flex min-w-0 flex-1 items-center justify-center px-5 py-10 sm:px-10 lg:px-14 lg:py-14">
          {children}
        </div>
        <footer className="px-5 pb-6 text-center text-[11px] text-[#87928e] sm:px-10 lg:text-right">
          © 2026 BMS. Authorized access only.
        </footer>
      </section>
    </div>
  );
}
