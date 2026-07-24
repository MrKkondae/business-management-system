import type { NextConfig } from "next";

const backendOrigin =
  process.env.BMS_BACKEND_ORIGIN?.replace(/\/+$/, "") ??
  "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/backend-api/:path*",
        destination: `${backendOrigin}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
