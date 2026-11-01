import { expect, test, type Page } from '@playwright/test';
import * as XLSX from 'xlsx';
import { login, openScreen, searchFor } from './helpers';

/**
 * 관리 › 다국어 — 엑셀 다운로드(지금 보이는 행)와 업로드(미리보기 → 새로·바뀐 것만 저장). 시험용 코드 e2e-ui.i18n.<RUN> 하나만 만들고 끝에 지운다.
 */
const RUN = Date.now();
const CODE = `e2e-ui.i18n.${RUN}`;

async function open(page: Page): Promise<void> {
  await openScreen(page, '관리', '다국어');
  const tab = page.getByRole('tab', { name: '다국어', exact: true });
  let stable = 0;
  for (let i = 0; i < 15 && stable < 3; i++) {
    if ((await tab.getAttribute('aria-selected')) === 'true') stable += 1; else { stable = 0; await tab.click(); }
    await page.waitForTimeout(500);
  }
}

function xlsx(rows: Record<string, unknown>[]): Buffer {
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(rows), '다국어');
  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }) as Buffer;
}

async function upload(page: Page, rows: Record<string, unknown>[]): Promise<void> {
  await page.getByLabel('i18n-excel-file').setInputFiles({ name: 'i18n.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: xlsx(rows) });
}

test('다국어 — 엑셀 다운로드·업로드(새로·같음·오류, 바뀜)', async ({ page }) => {
  await login(page);
  await open(page);

  // 다운로드 — 송수신 기록과 같은 창(지금 쪽·검색 결과 전체), 머리글과 첫 행
  await searchFor(page, 'i18n.menu.tc.home');
  await page.getByRole('button', { name: '엑셀 다운로드' }).filter({ visible: true }).click();
  const exportDialog = page.getByRole('dialog');
  await expect(exportDialog.getByText(/^지금 쪽 \(1~1\)$/)).toBeVisible();
  await expect(exportDialog.getByText('검색 결과 전체 (1)')).toBeVisible();
  await expect(exportDialog.getByText('1건을 내려받습니다.')).toBeVisible();
  const download = page.waitForEvent('download');
  await exportDialog.getByRole('button', { name: '내려받기' }).click();
  const file = await download;
  await expect(exportDialog).toBeHidden();
  expect(file.suggestedFilename()).toMatch(/\.xlsx$/);
  const wb = XLSX.read(await (await file.createReadStream()).toArray().then((c) => Buffer.concat(c as Buffer[])), { type: 'buffer' });
  const first = XLSX.utils.sheet_to_json<Record<string, string>>(wb.Sheets[wb.SheetNames[0]])[0];
  expect(Object.keys(first)).toEqual(['코드', '종류', 'KO', 'EN', 'JA', 'ZH', '설명', '사용']);
  expect(first['코드']).toBe('i18n.menu.tc.home');

  // 업로드 1 — 새 코드 1, 같음 1(받은 그대로), 오류 1
  await upload(page, [first, { 코드: CODE, 종류: '메시지', KO: '시험 문구', EN: '' }, { 코드: 'bad code', KO: 'x' }]);
  const dialog = page.getByRole('dialog');
  const summary = dialog.getByLabel('upload-summary');
  await expect(summary).toContainText('새로 1');
  await expect(summary).toContainText('같음 1');
  await expect(summary).toContainText('오류 1');
  await dialog.getByRole('button', { name: /반영/ }).click();
  await expect(page.getByText('1건을 저장했습니다')).toBeVisible();
  await dialog.getByRole('button', { name: '닫기' }).click();
  await searchFor(page, CODE);
  const row = page.getByRole('row').filter({ hasText: CODE }).filter({ visible: true });
  await expect(row).toContainText('시험 문구');

  // 업로드 2 — EN 만 바꿈, KO 칸 비움(지우지 않고 그대로)
  await upload(page, [{ 코드: CODE, KO: '', EN: 'test text' }]);
  await expect(summary).toContainText('바뀜 1');
  await dialog.getByRole('button', { name: /반영/ }).click();
  await expect(page.getByText('1건을 저장했습니다')).toBeVisible();
  await dialog.getByRole('button', { name: '닫기' }).click();
  await expect(row).toContainText('test text');
  await expect(row).toContainText('시험 문구');

  // 정리 — 이 시험이 만든 코드만
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem('wow-mes-session') ?? '{}').state?.token as string);
  expect((await page.request.delete(`/api/i18n/items/${encodeURIComponent(CODE)}`, { headers: { Authorization: `Bearer ${token}` } })).status()).toBe(204);
});
