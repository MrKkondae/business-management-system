import type { Metadata } from "next";
import { AuthGate } from "@/features/auth/AuthGate";
import { LoginForm } from "@/features/auth/LoginForm";

export const metadata: Metadata = {
  title: "로그인",
};

export default function LoginPage() {
  return (
    <AuthGate mode="public-only">
      <LoginForm />
    </AuthGate>
  );
}
