import { defineConfig } from '@playwright/test';

/**
 * 화면 e2e (docs/frontend-design.md F9) — 실제 tc·vplc 를 띄운 상태에서 돈다. 대상은 개발 서버(5180) 또는 tc 가 서빙하는 빌드.
 * 브라우저는 설치된 Chrome 을 쓴다(따로 받지 않는다).
 *
 *   TC_UI_URL=http://localhost:5180 TC_UI_E2E_ID=… TC_UI_E2E_PASSWORD_FILE=… npx playwright test
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.TC_UI_URL ?? 'http://localhost:5180',
    channel: 'chrome',
    headless: true,
    viewport: { width: 1440, height: 900 },
    locale: 'ko-KR',
    screenshot: 'only-on-failure',
  },
});
