import { expect, test, type Page } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * 가상 PLC 연결 — 목록의 연결 클라이언트 보기(작업 메뉴·연결 수 칸)와 가상 PLC 연결 이력 화면. 바꾸는 것은 없다.
 * 전제: 게시·실행 중인 가상 PLC 가 있고 클라이언트가 하나 이상 붙어 있다 — 이름은 TC_UI_VPLC(기본 plc-e2e 가 붙는 vplc-e2e).
 */
const VPLC = process.env.TC_UI_VPLC ?? 'vplc-e2e';

test.beforeEach(async ({ page }) => {
  await login(page);
});

async function expectClientsDialog(page: Page): Promise<void> {
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('연결 클라이언트 보기')).toBeVisible();
  await expect(dialog.getByText(VPLC, { exact: true })).toBeVisible();
  const table = dialog.getByRole('table', { name: 'connected-clients' });
  await expect(table.getByRole('rowheader', { name: /포트 번호/ })).toBeVisible();
  await expect(table.getByRole('rowheader', { name: /연결 수/ })).toBeVisible();
  await expect(table.getByText('127.0.0.1').first()).toBeVisible();
}

test('가상 PLC 목록 — 연결 클라이언트 보기(작업 메뉴, 연결 수 칸)', async ({ page }) => {
  await openScreen(page, '가상 PLC', '가상 PLC 목록');
  await page.getByRole('tab', { name: '가상 PLC 목록', exact: true }).click();
  await searchFor(page, VPLC);
  const row = page.getByRole('row').filter({ hasText: VPLC }).filter({ visible: true }).first();

  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '연결 클라이언트 보기' }).click();
  await expectClientsDialog(page);
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/vplc-clients.png' });
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toBeHidden();

  await row.getByRole('button', { name: `clients-${VPLC}` }).click();
  await expectClientsDialog(page);
  await page.keyboard.press('Escape');
});

test('가상 PLC 연결 이력 — 이름·종류·기간으로 찾기', async ({ page }) => {
  await openScreen(page, '가상 PLC', '가상 PLC 연결 이력');
  await page.getByRole('tab', { name: '가상 PLC 연결 이력', exact: true }).click();
  await expect(page.getByText(/가상 PLC 연결 이력\s*\(\d[\d,]*\)/).filter({ visible: true })).toBeVisible();

  await searchFor(page, VPLC);
  await page.getByRole('button', { name: '검색', exact: true }).filter({ visible: true }).click();
  await expect(page.getByRole('row').filter({ hasText: VPLC }).filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: '연결' }).filter({ visible: true }).first()).toBeVisible();
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/vplc-connhist.png' });

  // 종류 OPC UA 로 거르면 이 가상 PLC(MELSEC·Modbus)의 이력은 빠진다
  await page.getByLabel('가상 PLC 종류').filter({ visible: true }).click();
  await page.getByRole('option', { name: 'OPC UA' }).click();
  await page.getByRole('button', { name: '검색', exact: true }).filter({ visible: true }).click();
  await expect(page.getByRole('row').filter({ hasText: VPLC }).filter({ visible: true })).toHaveCount(0);
});
