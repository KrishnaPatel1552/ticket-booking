import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    css: true,
  },
  server: {
    proxy: {
      "/api": `http://localhost:${process.env.VITE_API_PORT ?? "8080"}`,
    },
  },
});
