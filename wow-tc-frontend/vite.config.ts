import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/** 빌드 식별자(yyMMdd-HHmm) — 화면(머리말)에 보여 "지금 보는 게 최신 배포본인가"를 확인한다(mes-dev 와 같음). */
const pad2 = (n: number) => String(n).padStart(2, '0');
const builtAt = new Date();
const BUILD_ID = `${String(builtAt.getFullYear()).slice(2)}${pad2(builtAt.getMonth() + 1)}${pad2(builtAt.getDate())}`
  + `-${pad2(builtAt.getHours())}${pad2(builtAt.getMinutes())}`;

/** 개발 서버가 넘길 곳 — 바꾸려면 TC_DEV_API·VPLC_DEV_API */
const TC_API = process.env.TC_DEV_API ?? 'http://localhost:8087';
const VPLC_API = process.env.VPLC_DEV_API ?? 'http://localhost:8088';

export default defineConfig({
  define: { __APP_BUILD__: JSON.stringify(BUILD_ID) },
  plugins: [react()],
  server: {
    port: 5180,
    // 같은 출처로 보이게 한다 — 새로 고침 쿠키(/api/auth)·SSE(/api/sse) 가 그대로 된다. Origin 을 지워 백엔드 CORS 검사를 피한다(mes-dev 와 같음).
    proxy: {
      '/api': {
        target: TC_API,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
      // 가상 PLC(시험 장비) — 운영은 tc-config.json 의 vplcUrl 로 바로 부른다(결정 F8)
      '/vplc-api': {
        target: VPLC_API,
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/vplc-api/, ''),
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
        },
      },
    },
  },
  // 화면마다 지연 로드라 처음 연 화면이 새 의존성(아이콘 등)을 찾으면 개발 서버가 다시 묶고 페이지를 새로 고친다 — 시작할 때 모든 화면을 훑는다
  optimizeDeps: {
    entries: ['index.html', 'src/**/*.tsx'],
  },
  build: {
    chunkSizeWarningLimit: 2000,
  },
});
