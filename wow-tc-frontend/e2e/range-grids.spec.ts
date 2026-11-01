import { expect, test, type Page } from '@playwright/test';
import { login, openScreen } from './helpers';

/**
 * 구간 스크롤 표(docs/range-grid-design.md) — 송수신 기록·스케줄 Job 실행 이력·설정 변경 이력·가상 PLC 연결 이력.
 * 제목·건수, 번호 칸, 아래 쪽 넘김 없음, 스크롤하면 그 구간을 받음, 한 번에 받는 행 수, 엑셀.
 * 송수신 기록의 구간 이동은 opcode TC_UI_RANGE_OPCODE(기본 rt.range)의 기록이 10만 건을 넘을 때만 본다(없으면 건너뜀).
 */
const OPCODE = process.env.TC_UI_RANGE_OPCODE ?? 'rt.range';

test.beforeEach(async ({ page }) => {
  await login(page);
});

async function open(page: Page, group: string, label: string): Promise<void> {
  await openScreen(page, group, label);
  // 셸이 첫 로그인 뒤 늦게 홈 탭을 다시 고르는 일이 있다 — 화면 탭이 선택된 채로 남을 때까지 다시 고른다
  const tab = page.getByRole('tab', { name: label, exact: true });
  for (let i = 0; i < 5; i++) {
    await tab.click();
    await page.waitForTimeout(800);
    if ((await tab.getAttribute('aria-selected')) === 'true') return;
  }
  await expect(tab).toHaveAttribute('aria-selected', 'true');
}

const grid = (page: Page) => page.locator('.MuiDataGrid-root').filter({ visible: true }).first();

/** 번호 칸(첫 데이터 칸)의 맨 위 값 */
async function firstNo(page: Page): Promise<string> {
  const cell = grid(page).locator('.MuiDataGrid-row[data-rowindex="0"] [data-field="_no"]');
  await expect(cell).not.toHaveText('');
  return (await cell.textContent())?.trim() ?? '';
}

async function expectRangeGrid(page: Page, title: string): Promise<void> {
  await expect(page.getByText(new RegExp(`^${title}\\s*\\(\\d[\\d,]*\\)$`)).filter({ visible: true })).toBeVisible();
  await expect(grid(page).getByRole('columnheader', { name: '번호' })).toBeVisible();
  await expect(grid(page).locator('.MuiTablePagination-root')).toHaveCount(0);
  await expect(page.getByLabel('한 번에 받는 행 수').filter({ visible: true })).toBeVisible();
  expect(await firstNo(page)).toBe('1');
}

test('송수신 기록 — 스크롤로 구간 받기, 구간 이동, 행 수, 엑셀 창', async ({ page }) => {
  await open(page, '운영', '송수신 기록');
  await page.getByLabel('opcode').filter({ visible: true }).fill(OPCODE);
  await page.getByRole('button', { name: '검색', exact: true }).filter({ visible: true }).click();
  // 검색 뒤 새 건수가 올 때까지(검색 직후에는 앞 결과의 행이 잠시 남아 있다)
  const title = page.getByText(/^송수신 목록\s*\(/).filter({ visible: true });
  await expect(title).not.toHaveText(/\(0\)$/, { timeout: 15_000 });
  await expectRangeGrid(page, '송수신 목록');
  const total = Number(((await title.textContent()) ?? '').replace(/[^\d]/g, ''));
  test.skip(total <= 100_000, `opcode ${OPCODE} 기록이 10만 건 이하(${total}) — 구간 이동 시험 건너뜀`);

  // 스크롤 — 받지 않은 자리로 내려가면 그 구간을 받아 번호가 이어진다
  const scroller = grid(page).locator('.MuiDataGrid-virtualScroller');
  await scroller.evaluate((el) => { el.scrollTop = 36 * 5000; });
  await expect(grid(page).locator('[data-field="_no"]').filter({ hasText: /^[5-9],\d{3}$/ }).first()).toBeVisible({ timeout: 15_000 });

  // 구간 이동 — 다음 구간의 첫 행은 100,001
  await expect(page.getByLabel('range-segment')).toContainText(`1~100,000 / ${total.toLocaleString()}`);
  await page.getByRole('button', { name: '다음 구간' }).click();
  await expect(page.getByLabel('range-segment')).toContainText(`100,001~${total.toLocaleString()}`);
  await expect.poll(() => firstNo(page)).toBe('100,001');
  await page.getByRole('button', { name: '이전 구간' }).click();
  await expect.poll(() => firstNo(page)).toBe('1');

  // 메모리 — 한 구간(10만)의 스켈레톤을 쥔 뒤의 JS 힙
  const heap = await page.evaluate(() => (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory?.usedJSHeapSize ?? 0);
  console.log(`JS heap after 100k-row segment: ${(heap / 1048576).toFixed(1)} MB`);

  // 한 번에 받는 행 수 — 직접 입력
  const chunk = page.getByLabel('한 번에 받는 행 수').filter({ visible: true });
  await chunk.fill('300');
  await chunk.press('Tab');
  await expect(chunk).toHaveValue('300');
  await expect.poll(() => firstNo(page)).toBe('1');

  // 엑셀 창 — 지금 구간·전체, 20만 넘으면 막힘(여기서는 건수만 확인하고 닫는다)
  await page.getByRole('button', { name: '엑셀 다운로드' }).filter({ visible: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText(`검색 결과 전체 (${total.toLocaleString()})`)).toBeVisible();
  await expect(dialog.getByText('본문(JSON) 포함')).toBeVisible();
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/range-audit-export.png' });
  await dialog.getByRole('button', { name: '닫기' }).click();
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/range-audit.png' });
});

test('스케줄 Job 실행 이력 — 구간 스크롤 표', async ({ page }) => {
  await open(page, '운영', '스케줄 Job 실행 이력');
  await expectRangeGrid(page, '스케줄 Job 실행 이력');
});

test('설정 변경 이력 — 구간 스크롤 표, 엑셀 내려받기', async ({ page }) => {
  await open(page, '운영', '설정 변경 이력');
  await expectRangeGrid(page, '설정 변경 이력');
  await page.getByRole('button', { name: '엑셀 다운로드' }).filter({ visible: true }).click();
  const dialog = page.getByRole('dialog');
  const download = page.waitForEvent('download');
  await dialog.getByRole('button', { name: '내려받기' }).click();
  const file = await download;
  expect(file.suggestedFilename()).toMatch(/^설정변경이력_.*\.xlsx$/);
  await expect(dialog).toBeHidden();
});

test('가상 PLC 연결 이력 — 구간 스크롤 표', async ({ page }) => {
  await open(page, '가상 PLC', '가상 PLC 연결 이력');
  await expectRangeGrid(page, '가상 PLC 연결 이력');
});
