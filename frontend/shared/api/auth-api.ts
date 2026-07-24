import {
  apiRequest,
  ensureCsrfToken,
} from "@/shared/api/api-client";
import { clearCsrfToken } from "@/shared/api/csrf-token-store";
import type {
  CurrentUserResponse,
  LoginRequest,
} from "@/shared/types/auth";

export function getCurrentUser(): Promise<CurrentUserResponse> {
  return apiRequest<CurrentUserResponse>("/auth/me");
}

export function prepareCsrfToken(): Promise<unknown> {
  return ensureCsrfToken();
}

export async function login(
  request: LoginRequest,
): Promise<CurrentUserResponse> {
  const response = await apiRequest<CurrentUserResponse>("/auth/login", {
    method: "POST",
    body: request,
    csrf: true,
  });
  clearCsrfToken();
  return response;
}

export async function logout(): Promise<void> {
  await apiRequest<void>("/auth/logout", {
    method: "POST",
    csrf: true,
  });
  clearCsrfToken();
}

export function recordUserActivity(): Promise<void> {
  return apiRequest<void>("/auth/activity", {
    method: "POST",
    csrf: true,
  });
}
