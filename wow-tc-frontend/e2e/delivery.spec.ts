import { expect, test, type Page } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * 전달 화면(5-4) — WebHook·전달 정의·외부 DB 연결. 만든 것(e2e-ui-*)은 끝에 지운다.
 * DB 연결 등록에는 tc 의 TC_SECRET_KEY 가 있어야 한다(비밀번호 암호화).
 */
const RUN = Date.now();
const HOOK = `e2e-ui-hook-${RUN}`;

test.beforeEach(async ({ page }) => {
  await login(page);
});

async function rowMenu(page: Page, text: string, item: string) {
  const row = page.getByRole('row').filter({ hasText: text }).filter({ visible: true }).first();
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: item }).click();
}

async function confirm(page: Page) {
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
}

test('WebHook·전달 정의 — 등록, 목록, 사용 끄기, 지우기', async ({ page }) => {
  // WebHook 등록 — URL 형식 검사 후 저장
  await openScreen(page, '전달', 'WebHook');
  await page.getByRole('button', { name: '추가' }).click();
  let dialog = page.getByRole('dialog');
  await dialog.getByLabel('이름').fill(HOOK);
  await dialog.getByLabel('URL').fill('not-a-url');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog.getByText('http(s):// 로 시작하는 주소')).toBeVisible();
  await dialog.getByLabel('URL').fill('http://127.0.0.1:18099/hook');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();
  await searchFor(page, HOOK);
  await expect(page.getByRole('row').filter({ hasText: HOOK })).toHaveCount(1);

  // 전달 정의 — plc.e2e.d100.read → 이 WebHook, 실패일 때만
  await openScreen(page, '전달', '전달 정의');
  await page.getByRole('button', { name: '추가' }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel('인터페이스(opcode)').fill('plc.e2e.d100.read');
  await page.getByRole('option', { name: 'plc.e2e.d100.read', exact: true }).click();
  await dialog.getByLabel('WebHook').click();
  await page.getByRole('option', { name: HOOK }).click();
  await dialog.getByLabel('보낼 때').click();
  await page.getByRole('option', { name: '실패일 때만' }).click();
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();
  await searchFor(page, HOOK);
  const rule = page.getByRole('row').filter({ hasText: HOOK }).first();
  await expect(rule.getByText('실패일 때만')).toBeVisible();
  await rule.getByRole('switch').or(rule.getByRole('checkbox')).first().click();
  await expect(rule).toHaveClass(/row--inactive/);
  await rowMenu(page, HOOK, '삭제');
  await confirm(page);
  await expect(page.getByRole('row').filter({ hasText: HOOK })).toHaveCount(0);

  // WebHook 지우기
  await openScreen(page, 'PLC', '커넥터');
  await openScreen(page, '전달', 'WebHook');
  await searchFor(page, HOOK);
  await rowMenu(page, HOOK, '삭제');
  await confirm(page);
  await expect(page.getByRole('row').filter({ hasText: HOOK })).toHaveCount(0);
});

test('외부 DB 연결 — 등록(비밀번호 저장됨), 연결 시험 실패 알림, 지우기', async ({ page }) => {
  const name = `e2e-ui-db-${RUN}`;
  await openScreen(page, '전달', '외부 DB 연결');
  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('이름').fill(name);
  await dialog.getByLabel('JDBC URL').fill('jdbc:mariadb://127.0.0.1:3399/none');
  await dialog.getByLabel('사용자').fill('nobody');
  await dialog.getByLabel('비밀번호').fill(`pw-${RUN}`);
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();
  await searchFor(page, name);
  const row = page.getByRole('row').filter({ hasText: name }).first();
  await expect(row.getByText('저장됨')).toBeVisible();

  await row.getByRole('button', { name: '연결 시험' }).or(row.getByRole('menuitem', { name: '연결 시험' })).first().click();
  await expect(page.getByRole('dialog').getByText(`연결 시험 — ${name}`)).toBeVisible();
  await page.getByRole('dialog').getByRole('button').last().click();

  await rowMenu(page, name, '삭제');
  await confirm(page);
  await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(0);
});
