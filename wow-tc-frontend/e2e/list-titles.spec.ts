import { expect, test, type Page } from '@playwright/test';
import { login, openScreen } from './helpers';

/**
 * 목록 표 위 제목 줄(글머리·제목·건수)과 아래 쪽 넘김 — PLC 인터페이스, WebHook, 외부 연결 DB(커넥터와 같은 모양).
 * 바꾸는 것은 없다. 건수는 "(N)" 으로 보이고, 아래 줄은 "총 N건" 대신 쪽 크기·쪽 넘김이다.
 */
test.beforeEach(async ({ page }) => {
  await login(page);
});

async function expectTitledPagedList(page: Page, title: string): Promise<void> {
  // 열린 탭마다 화면이 있어 보이는 것만
  const heading = page.getByText(title, { exact: false }).filter({ visible: true }).first();
  await expect(heading).toBeVisible();
  await expect(heading).toHaveText(new RegExp(title + '\\s*\\(\\d[\\d,]*\\)'));
  const footer = page.locator('.MuiTablePagination-root').filter({ visible: true });
  await expect(footer).toBeVisible();
  await expect(footer.getByRole('button', { name: /first page|처음/i })).toBeVisible();
  await expect(page.getByText(/^총 [\d,]+건$/).filter({ visible: true })).toHaveCount(0);
}

test('인터페이스 — 제목·건수, 쪽 넘김', async ({ page }) => {
  await openScreen(page, 'PLC', '인터페이스');
  await expectTitledPagedList(page, '인터페이스 목록');
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/interface.png' });
});

test('WebHook — 제목·건수, 쪽 넘김', async ({ page }) => {
  await openScreen(page, '전달', 'WebHook');
  await expectTitledPagedList(page, 'WebHook 목록');
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/webhook.png' });
});

test('외부 연결 DB — 제목·건수, 쪽 넘김', async ({ page }) => {
  await openScreen(page, '전달', '외부 DB 연결');
  await expectTitledPagedList(page, '외부 연결 DB 목록');
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/dbconn.png' });
});
