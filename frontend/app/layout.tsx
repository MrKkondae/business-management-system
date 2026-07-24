import type { Metadata } from "next";
import { AuthProvider } from "@/features/auth/AuthProvider";
import { SessionActivityTracker } from "@/features/session/SessionActivityTracker";
import "./globals.css";

export const metadata: Metadata = {
  title: {
    default: "BMS",
    template: "%s | BMS",
  },
  description: "업무 운영을 연결하는 Business Management System",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="min-h-full">
        <AuthProvider>
          {children}
          <SessionActivityTracker />
        </AuthProvider>
      </body>
    </html>
  );
}
