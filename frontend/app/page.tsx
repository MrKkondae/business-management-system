import type { Metadata } from "next";
import { HomeClient } from "@/features/auth/HomeClient";

export const metadata: Metadata = {
  title: "공통 애플리케이션",
};

export default function HomePage() {
  return <HomeClient />;
}
