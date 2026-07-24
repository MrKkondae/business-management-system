import type { Metadata } from "next";
import { InitialRegistrationForm } from "@/features/account/initial-registration/InitialRegistrationForm";
import { AuthGate } from "@/features/auth/AuthGate";

export const metadata: Metadata = {
  title: "최초 등록",
};

export default function InitialRegistrationPage() {
  return (
    <AuthGate mode="limited-only">
      <InitialRegistrationForm />
    </AuthGate>
  );
}
