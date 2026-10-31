import { randomBytes } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * 관리 화면(5-6) — 사용자·API 클라이언트·운영 옵션·다국어·공통 코드. 만드는 것(e2e-ui-*)은 끝에 지운다. 운영 옵션은 바꾸지 않고 보기만 한다.
 */
const RUN = Date.now();

test.beforeEach(async ({ page }) => {
  await login(page);
});

/** 행의 … 메뉴에서 고른다 */
async function rowMenu(row: ReturnType<import('@playwright/test').Page['getByRole']>, page: import('@playwright/test').Page, item: string) {
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: item }).click();
}

test('사용자 — 등록·사용 이력·비밀번호 초기화·사용 중지·삭제', async ({ page }) => {
  const id = `e2e-ui-user-${RUN}`;
  await openScreen(page, '관리', '사용자');
  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('아이디').fill(id);
  await dialog.getByLabel('이름').fill('화면 시험');
  await dialog.getByLabel('운영자').check();
  await dialog.getByLabel('초기 비밀번호').fill('short');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog.getByText('10자 이상, 아이디와 달라야 합니다')).toBeVisible(); // 화면 검사
  await dialog.getByLabel('초기 비밀번호').fill(randomBytes(12).toString('base64url'));
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  await searchFor(page, id);
  const row = page.getByRole('row').filter({ hasText: id }).filter({ visible: true }).first();
  await expect(row.getByText('운영자')).toBeVisible();
  await expect(row.getByText('조회자')).toBeVisible(); // 기본 역할도 남아 있다
  await expect(row.getByText('비밀번호 바꿀 차례')).toBeVisible();

  await rowMenu(row, page, '비밀번호 초기화');
  const reset = page.getByRole('dialog');
  await reset.getByLabel('새 비밀번호').fill(randomBytes(12).toString('base64url'));
  await reset.getByRole('button', { name: '비밀번호 초기화' }).click();
  await expect(reset).toBeHidden();

  await rowMenu(row, page, '사용 이력');
  await expect(page.getByRole('dialog').getByText(`사용 이력 — ${id}`)).toBeVisible();
  await page.keyboard.press('Escape');

  await rowMenu(row, page, '사용 중지');
  await page.getByRole('dialog').getByRole('button', { name: /확인|OK/ }).last().click();
  await expect(row.getByText('사용 중지')).toBeVisible();

  await rowMenu(row, page, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: id }).filter({ visible: true })).toHaveCount(0);
});

test('API 클라이언트 — 등록 후 키 발급(한 번만 보임)·폐기·삭제', async ({ page }) => {
  const id = `e2e-ui-client-${RUN}`;
  await openScreen(page, '관리', 'API 클라이언트');
  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('클라이언트 ID').fill(id);
  await dialog.getByLabel('이름').fill('화면 시험');
  await dialog.getByLabel('허용 opcode').fill('plc.e2e.d100.read');
  await page.getByRole('option', { name: 'plc.e2e.d100.read', exact: true }).click();
  await dialog.getByLabel('허용 IP').fill('127.0.0.1');
  await dialog.getByLabel('허용 IP').press('Enter');
  await dialog.getByRole('button', { name: '저장' }).click();

  // 새 클라이언트는 바로 키 화면이 열린다
  const keys = page.getByRole('dialog').filter({ hasText: `API 키 — ${id}` });
  await expect(keys).toBeVisible();
  await keys.getByLabel('수명(일)').fill('30');
  await keys.getByRole('button', { name: '새 키 발급' }).click();
  await expect(keys.getByText('지금 한 번만 보입니다', { exact: false })).toBeVisible();
  await expect(keys.getByLabel('새 키')).not.toHaveValue('');
  const keyRow = keys.getByRole('row').filter({ hasText: '활성' });
  await expect(keyRow).toHaveCount(1);
  await keyRow.getByRole('button', { name: '폐기' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /확인|OK/ }).last().click();
  await expect(keys.getByRole('gridcell', { name: '폐기', exact: true })).toHaveCount(1);
  await keys.getByRole('button', { name: '닫기' }).last().click();

  await searchFor(page, id);
  const row = page.getByRole('row').filter({ hasText: id }).filter({ visible: true }).first();
  await expect(row.getByText('plc.e2e.d100.read')).toBeVisible();
  await rowMenu(row, page, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: id }).filter({ visible: true })).toHaveCount(0);
});

test('운영 옵션 — 권한 검사 모드·전환 스위치를 보인다', async ({ page }) => {
  await openScreen(page, '관리', '운영 옵션');
  await expect(page.getByText('권한 검사 모드', { exact: true }).filter({ visible: true })).toBeVisible();
  await expect(page.getByRole('radio', { checked: true }).filter({ visible: true })).toHaveCount(1);
  await expect(page.getByRole('switch', { name: '기존 JWT 호출자 전환 스위치' }).or(page.getByRole('checkbox', { name: '기존 JWT 호출자 전환 스위치' }))).toHaveCount(1);
});

test('다국어 — 찾고, 등록·수정·삭제', async ({ page }) => {
  const code = `i18n.e2e.ui.${RUN}`;
  await openScreen(page, '관리', '다국어');
  await searchFor(page, 'i18n.menu.tc.admin.i18n');
  await expect(page.getByRole('row').filter({ hasText: 'i18n.menu.tc.admin.i18n' }).filter({ visible: true }).getByText('多言語')).toBeVisible();

  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: /^코드/ }).fill(code);
  await dialog.getByLabel('KO').fill('시험 문구');
  await dialog.getByLabel('EN').fill('Test label');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  await searchFor(page, code);
  const row = page.getByRole('row').filter({ hasText: code }).filter({ visible: true }).first();
  await expect(row.getByText('Test label')).toBeVisible();
  await row.getByRole('button', { name: '수정' }).click();
  await page.getByRole('dialog').getByLabel('JA').fill('試験');
  await page.getByRole('dialog').getByRole('button', { name: '저장' }).click();
  await expect(row.getByText('試験')).toBeVisible();

  await rowMenu(row, page, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: code }).filter({ visible: true })).toHaveCount(0);
});

test('공통 코드 — 그룹과 코드 등록·지우기', async ({ page }) => {
  const group = `common.code.e2eui${RUN}`;
  await openScreen(page, '관리', '공통 코드');
  await page.getByRole('button', { name: '추가' }).click();
  let dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: /^코드/ }).fill(group);
  await dialog.getByLabel('이름').fill('화면 시험 그룹');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  await searchFor(page, group);
  await page.getByRole('row').filter({ hasText: group }).filter({ visible: true }).first().click();
  await page.getByRole('button', { name: '코드 등록' }).click();
  dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('textbox', { name: /^코드/ })).toHaveValue(`${group}.`);
  await dialog.getByRole('textbox', { name: /^코드/ }).fill(`${group}.a`);
  await dialog.getByLabel('이름').fill('가');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();
  const codeRow = page.getByRole('row').filter({ hasText: `${group}.a` }).filter({ visible: true });
  await expect(codeRow).toHaveCount(1);

  // 하위 코드가 있으면 그룹은 못 지운다
  const groupRow = page.getByRole('row').filter({ hasText: '화면 시험 그룹' }).filter({ visible: true }).first();
  await rowMenu(groupRow, page, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByText('하위 코드가 있어', { exact: false })).toBeVisible();

  await rowMenu(codeRow.first(), page, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(codeRow).toHaveCount(0);
  await rowMenu(groupRow, page, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: group }).filter({ visible: true })).toHaveCount(0);
});
