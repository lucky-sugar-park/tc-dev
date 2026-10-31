import { expect, test, type Page } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * 가상 PLC 화면(5-7) — 목록(등록·게시·시작·정지·해제·삭제)과 메모리(값 쓰기·10진 칸 고치기·비트 켜기·문자열·지우기 없이 0 으로 되돌림).
 * 메모리는 vplc-e2e 의 D900~ 를 쓴다(다른 시험이 읽는 D100 근처는 건드리지 않는다). 만드는 가상 PLC(e2e-ui-vplc-*)는 끝에 지운다.
 */
const RUN = Date.now();

test.beforeEach(async ({ page }) => {
  await login(page);
});

async function rowMenu(page: Page, rowText: string, item: string) {
  const row = page.getByRole('row').filter({ hasText: rowText }).filter({ visible: true }).first();
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: item }).click();
}

test('가상 PLC 목록 — 등록·게시·시작·연결·정지·해제·삭제', async ({ page }) => {
  const name = `e2e-ui-vplc-${RUN}`;
  const port = 15900 + (RUN % 50);
  await openScreen(page, '가상 PLC', '가상 PLC 목록');
  await expect(page.getByRole('row').filter({ hasText: 'vplc-e2e' }).filter({ visible: true }).getByText('RUNNING')).toBeVisible();

  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: /^이름/ }).fill(name);
  await dialog.getByLabel('시작 포트').fill(String(port));
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  await searchFor(page, name);
  const row = page.getByRole('row').filter({ hasText: name }).filter({ visible: true }).first();
  await expect(row.getByText('REGISTERED')).toBeVisible();
  await rowMenu(page, name, '게시');
  await expect(row.getByText('PUBLISHED')).toBeVisible();
  await row.getByRole('button', { name: '시작' }).click();
  await expect(row.getByText('RUNNING')).toBeVisible();
  await rowMenu(page, name, '연결');
  await expect(page.getByRole('dialog').getByRole('row').filter({ hasText: String(port) })).toHaveCount(1);
  await page.keyboard.press('Escape');
  await rowMenu(page, name, '정지');
  await expect(row.getByText('STOPPED')).toBeVisible();
  await rowMenu(page, name, '해제');
  await expect(row.getByText('RELEASED')).toBeVisible();
  await rowMenu(page, name, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: name }).filter({ visible: true })).toHaveCount(0);
});

test('가상 PLC 메모리 — 값 쓰기·10진 칸 고치기·비트·문자열', async ({ page }) => {
  await openScreen(page, '가상 PLC', '메모리 보기·제어');
  await expect(page.getByRole('combobox', { name: '가상 PLC' }).filter({ visible: true })).toContainText('vplc-e2e');
  const start = page.getByRole('spinbutton', { name: '시작 주소' }).filter({ visible: true });
  await start.fill('900');
  await start.press('Enter');
  await expect(page.getByText('D900 ~ D919')).toBeVisible();

  const dialog = page.getByRole('dialog');
  const writeWords = async (values: string) => {
    await page.getByTestId('command-bar-slot').getByRole('button', { name: '값 쓰기' }).click();
    await dialog.getByLabel('값(쉼표로 여러 개)').fill(values);
    await dialog.getByRole('button', { name: '값 쓰기' }).click();
    await expect(dialog).toBeHidden();
  };
  await writeWords('0,0,0,0,0'); // 앞선 실행이 남긴 값을 지운다

  // 여러 값 쓰기 — 1234, -1, 0x00FF
  await page.getByTestId('command-bar-slot').getByRole('button', { name: '값 쓰기' }).click();
  await dialog.getByLabel('값(쉼표로 여러 개)').fill('1234, -1, 0x00FF');
  await dialog.getByRole('button', { name: '값 쓰기' }).click();
  await expect(dialog).toBeHidden();
  const r900 = page.getByRole('row').filter({ hasText: 'D900' });
  await expect(r900).toContainText('1234');
  await expect(r900).toContainText('04D2');
  await expect(page.getByRole('row').filter({ hasText: 'D901' })).toContainText('65535');
  await expect(page.getByRole('row').filter({ hasText: 'D902' })).toContainText('00FF');

  // 10진 칸을 고쳐 쓰기
  await r900.getByRole('gridcell').nth(1).dblclick();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type('42');
  await page.keyboard.press('Enter');
  await expect(r900).toContainText('002A');

  // 비트 켜기 — D903 의 b0
  const r903 = page.getByRole('row').filter({ hasText: 'D903' });
  await r903.getByRole('button', { name: 'b0 0' }).click();
  await expect(r903).toContainText('0001');

  // 문자열 — "AB" 는 한 워드(낮은 바이트가 앞 글자)
  await page.getByTestId('command-bar-slot').getByRole('button', { name: '값 쓰기' }).click();
  await dialog.getByRole('button', { name: '문자열(ASCII)' }).click();
  await dialog.getByLabel('시작 주소').fill('904');
  await dialog.getByRole('textbox', { name: '문자열' }).fill('AB');
  await dialog.getByRole('button', { name: '값 쓰기' }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole('row').filter({ hasText: 'D904' })).toContainText('4241');
  await expect(page.getByRole('tab', { name: /메모리 보기·제어/ })).toHaveCount(1); // 주소를 다시 써서 탭이 늘지 않는다
  await page.screenshot({ path: 'test-results/vplc-memory.png' });

  // 되돌린다
  await writeWords('0,0,0,0,0');
  await expect(r900).toContainText('0000');
});
