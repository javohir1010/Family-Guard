import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  // Backend address for the dev proxy; override in frontend/.env.local,
  // e.g. FG_BACKEND=localhost:8001 when port 8000 is taken
  const backend = loadEnv(mode, process.cwd(), "").FG_BACKEND || "localhost:8000";
  return {
    plugins: [react()],
    server: {
      port: 3000,
      proxy: {
        "/api": {
          target: `http://${backend}`,
          changeOrigin: true,
        },
        "/ws": {
          target: `ws://${backend}`,
          ws: true,
        },
      },
    },
  };
});
