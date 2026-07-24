"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { ApiClientError } from "@/shared/api/api-client";
import { getCurrentUser } from "@/shared/api/auth-api";
import { clearCsrfToken } from "@/shared/api/csrf-token-store";
import type { CurrentUserResponse } from "@/shared/types/auth";

export type AuthStatus =
  | "loading"
  | "unauthenticated"
  | "limited"
  | "authenticated"
  | "error";

type AuthContextValue = {
  status: AuthStatus;
  session: CurrentUserResponse | null;
  bootstrapError: string | null;
  bootstrapTraceId: string | null;
  sessionNotice: string | null;
  refresh: () => Promise<CurrentUserResponse | null>;
  establish: (session: CurrentUserResponse) => void;
  clear: () => void;
  expire: () => void;
  consumeSessionNotice: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function statusFor(session: CurrentUserResponse): AuthStatus {
  return session.passwordChangeRequired ? "limited" : "authenticated";
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [session, setSession] = useState<CurrentUserResponse | null>(null);
  const [bootstrapError, setBootstrapError] = useState<string | null>(null);
  const [bootstrapTraceId, setBootstrapTraceId] = useState<string | null>(null);
  const [sessionNotice, setSessionNotice] = useState<string | null>(null);
  const initialized = useRef(false);

  const clear = useCallback(() => {
    clearCsrfToken();
    setSession(null);
    setBootstrapError(null);
    setBootstrapTraceId(null);
    setSessionNotice(null);
    setStatus("unauthenticated");
  }, []);

  const establish = useCallback((nextSession: CurrentUserResponse) => {
    setSession(nextSession);
    setBootstrapError(null);
    setBootstrapTraceId(null);
    setSessionNotice(null);
    setStatus(statusFor(nextSession));
  }, []);

  const expire = useCallback(() => {
    clearCsrfToken();
    setSession(null);
    setBootstrapError(null);
    setBootstrapTraceId(null);
    setSessionNotice("세션이 만료되었습니다. 다시 로그인해 주세요.");
    setStatus("unauthenticated");
  }, []);

  const consumeSessionNotice = useCallback(() => {
    setSessionNotice(null);
  }, []);

  const refresh = useCallback(async () => {
    try {
      const nextSession = await getCurrentUser();
      establish(nextSession);
      return nextSession;
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) {
        clear();
        return null;
      }

      setSession(null);
      setBootstrapTraceId(
        error instanceof ApiClientError
          ? (error.problem?.traceId ?? null)
          : null,
      );
      setBootstrapError(
        error instanceof Error
          ? error.message
          : "로그인 상태를 확인하지 못했습니다.",
      );
      setStatus("error");
      return null;
    }
  }, [clear, establish]);

  useEffect(() => {
    if (initialized.current) {
      return;
    }
    initialized.current = true;
    void refresh();
  }, [refresh]);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      bootstrapError,
      bootstrapTraceId,
      sessionNotice,
      refresh,
      establish,
      clear,
      expire,
      consumeSessionNotice,
    }),
    [
      bootstrapError,
      bootstrapTraceId,
      clear,
      consumeSessionNotice,
      establish,
      expire,
      refresh,
      session,
      sessionNotice,
      status,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
