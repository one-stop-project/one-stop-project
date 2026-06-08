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
    // frontend/dist 로 빌드한다.
    // 이후 build.gradle 의 copyFrontendToStatic 태스크가
    //   frontend/dist → src/main/resources/static 으로 복사한다.
    // (Vite가 static 에 직접 쓰지 않고, Gradle 복사 파이프라인을 거치는 구조)
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
