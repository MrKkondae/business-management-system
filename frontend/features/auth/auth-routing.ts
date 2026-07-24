import type { AuthorizedMenu, CurrentUserResponse } from "@/shared/types/auth";

const implementedMenuUrls = new Set<string>();

export function firstImplementedMenu(
  menus: AuthorizedMenu[],
): AuthorizedMenu | null {
  return (
    [...menus]
      .filter((menu) => implementedMenuUrls.has(menu.menuUrl))
      .sort(
        (left, right) =>
          left.sortOrder - right.sortOrder ||
          left.menuId.localeCompare(right.menuId),
      )[0] ?? null
  );
}

export function resolvePostLoginPath(session: CurrentUserResponse): string {
  if (session.passwordChangeRequired) {
    return "/account/initial-registration";
  }
  return firstImplementedMenu(session.menus)?.menuUrl ?? "/";
}
