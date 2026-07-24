"use client";

import { useCallback, useEffect, useRef } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/AuthProvider";
import { ApiClientError } from "@/shared/api/api-client";
import { recordUserActivity } from "@/shared/api/auth-api";

const ACTIVITY_UPDATE_INTERVAL_MS = 60_000;

export function SessionActivityTracker() {
  const { status, expire } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const lastSentAt = useRef(0);
  const previousPath = useRef(pathname);
  const inFlight = useRef(false);

  const sendActivity = useCallback(async () => {
    if (
      (status !== "authenticated" && status !== "limited") ||
      inFlight.current ||
      Date.now() - lastSentAt.current < ACTIVITY_UPDATE_INTERVAL_MS
    ) {
      return;
    }

    inFlight.current = true;
    try {
      await recordUserActivity();
      lastSentAt.current = Date.now();
    } catch (error) {
      if (error instanceof ApiClientError && error.status === 401) {
        expire();
        router.replace("/login");
      }
    } finally {
      inFlight.current = false;
    }
  }, [expire, router, status]);

  useEffect(() => {
    if (previousPath.current !== pathname) {
      previousPath.current = pathname;
      void sendActivity();
    }
  }, [pathname, sendActivity]);

  useEffect(() => {
    if (status !== "authenticated" && status !== "limited") {
      lastSentAt.current = 0;
      return;
    }

    const onActivity = () => {
      void sendActivity();
    };
    const events: Array<keyof WindowEventMap> = [
      "pointerdown",
      "keydown",
      "scroll",
    ];
    events.forEach((eventName) =>
      window.addEventListener(eventName, onActivity, { passive: true }),
    );
    return () => {
      events.forEach((eventName) =>
        window.removeEventListener(eventName, onActivity),
      );
    };
  }, [sendActivity, status]);

  return null;
}
