type FormAlertProps = {
  tone?: "error" | "warning" | "info";
  message?: string | null;
  traceId?: string | null;
};

const toneClasses = {
  error:
    "border-[#efc4be] bg-[var(--danger-surface)] text-[var(--danger)]",
  warning:
    "border-[#ecd8a3] bg-[var(--warning-surface)] text-[var(--warning)]",
  info: "border-[var(--line)] bg-[var(--surface-subtle)] text-[var(--ink)]",
};

export function FormAlert({
  tone = "error",
  message,
  traceId,
}: FormAlertProps) {
  if (!message) {
    return null;
  }

  return (
    <div
      className={`border px-4 py-3 text-sm leading-6 ${toneClasses[tone]}`}
      role="alert"
      tabIndex={-1}
    >
      <p>{message}</p>
      {traceId ? (
        <p className="mt-1 text-xs opacity-75">추적 ID: {traceId}</p>
      ) : null}
    </div>
  );
}
