export function FieldError({
  id,
  children,
}: {
  id: string;
  children?: React.ReactNode;
}) {
  if (!children) {
    return null;
  }

  return (
    <p id={id} className="mt-2 text-sm font-medium text-[var(--danger)]">
      {children}
    </p>
  );
}
