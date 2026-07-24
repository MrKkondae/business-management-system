import { apiRequest } from "@/shared/api/api-client";
import { clearCsrfToken } from "@/shared/api/csrf-token-store";
import type { InitialRegistrationRequest } from "@/shared/types/auth";

export async function completeInitialRegistration(
  request: InitialRegistrationRequest,
): Promise<void> {
  await apiRequest<void>("/users/me/initial-registration", {
    method: "POST",
    body: request,
    csrf: true,
  });
  clearCsrfToken();
}
