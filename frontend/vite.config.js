import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';
export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            '@': path.resolve(__dirname, './src'),
        },
    },
    // ★ 프로덕션 빌드 설정
    build: {
        // Spring Boot 정적 리소스 폴더로 직접 출력
        // frontend/ 기준 상대경로 → ../src/main/resources/static
        outDir: 'dist',
        emptyOutDir: true,
    },
    server: {
        port: 3000,
        proxy: {
            // 개발 모드(npm run dev)에서만 사용
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
