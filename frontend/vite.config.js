import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig(({ command, mode }) => {
    const env = loadEnv(mode, process.cwd(), "VITE_");
    return {
        // 生产构建部署在子路径下；dev 仍用根路径，保持现有调试方式不变
        base: command === "build" ? env.VITE_PUBLIC_PATH || "/rag-agent-test-x9k3m7/" : "/",
        plugins: [react()],
        resolve: {
            alias: {
                "@": path.resolve(__dirname, "./src")
            }
        },
        build: {
            sourcemap: false
        },
        server: {
            host: "127.0.0.1",
            port: 5173,
            allowedHosts: ['yuyi0009.cn', '.yuyi0009.cn'],
            proxy: {
                "/api": {
                    target: "http://localhost:9090",
                    changeOrigin: true,
                    secure: false
                }
            }
        }
    };
});
