import type { CsrfTokenResponse } from "@/shared/types/auth";

let currentToken: CsrfTokenResponse | null = null;

export function getStoredCsrfToken(): CsrfTokenResponse | null {
  return currentToken;
}

export function storeCsrfToken(token: CsrfTokenResponse): void {
  currentToken = token;
}

export function clearCsrfToken(): void {
  currentToken = null;
}
