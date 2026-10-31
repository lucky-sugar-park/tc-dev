import { expect, test } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * PLC 화면(5-3) — 실제 tc·vplc(plc-e2e, D 메모리, 인터페이스 plc.e2e.d100.read — 점 d100·d101)가 있어야 한다(백엔드 e2e setup_e2e.py).
 * 바꾸는 것은 없다 — 미리보기·읽기·시험 요청(읽기)만.
 */
test.beforeEach(async ({ page }) => {
  await login(page);
});

test('홈 — 커넥터 연결 상태와 오늘 건수', async ({ page }) => {
  await expect(page.getByText('커넥터 연결 상태')).toBeVisible();
  await expect(page.getByRole('gridcell', { name: 'plc-e2e' }).first()).toBeVisible();
});

test('커넥터 — 목록, 등록 폼(프로토콜별 설정), 시험 읽기', async ({ page }) => {
  await openScreen(page, 'PLC', '커넥터');
  const row = page.getByRole('row').filter({ hasText: 'plc-e2e' });
  await expect(row).toBeVisible();
  await expect(row.getByText(/연결 \d/)).toBeVisible();

  // 등록 폼 — 프로토콜을 Modbus 로 바꾸면 설정 칸이 바뀐다
  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('커넥터 등록')).toBeVisible();
  await expect(dialog.getByLabel('frame')).toBeVisible();
  await dialog.getByLabel('프로토콜').click();
  await page.getByRole('option', { name: 'Modbus TCP' }).click();
  await expect(dialog.getByLabel('unitId')).toBeVisible();
  await expect(dialog.getByLabel('frame')).toHaveCount(0);
  await dialog.getByRole('button', { name: /close|닫기/i }).first().click();

  // 시험 읽기 — D100 INT16
  await row.getByRole('menuitem').or(row.getByLabel('more')).first().click().catch(async () => {
    await row.locator('button[aria-haspopup="menu"]').click();
  });
  await page.getByRole('menuitem', { name: '시험 읽기' }).click();
  const tr = page.getByRole('dialog');
  await tr.getByLabel('point-name-0').fill('d100');
  await tr.getByLabel('point-address-0').fill('D100');
  await tr.getByRole('button', { name: '읽기' }).click();
  await expect(tr.getByText('MELSEC_MC OK')).toBeVisible();
  await expect(tr.getByRole('cell', { name: 'd100', exact: true }).last()).toBeVisible();
});

test('인터페이스 — 목록(점 칸)과 상세 보기의 요청 미리보기', async ({ page }) => {
  await openScreen(page, 'PLC', '인터페이스');
  await searchFor(page, 'plc.e2e.d100.read');
  const row = page.getByRole('row').filter({ hasText: 'plc.e2e.d100.read' }).first();
  await expect(row.getByText('d100=D100, d101=D101')).toBeVisible();
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '상세 보기' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('cell', { name: 'D100', exact: true }).first()).toBeVisible();
  const plan = dialog.getByRole('table', { name: 'request-plan' });
  await expect(plan.getByRole('cell', { name: '0401/0000 일괄 읽기(워드)' })).toBeVisible();
  await dialog.getByRole('button', { name: /close|닫기/i }).first().click();
});

test('인터페이스 시험 — 읽기 요청 응답', async ({ page }) => {
  await openScreen(page, 'PLC', '인터페이스 시험');
  await page.getByLabel('인터페이스(opcode)').fill('plc.e2e.d100.read');
  await page.getByRole('option', { name: /^plc\.e2e\.d100\.read \(READ/ }).click();
  await page.getByRole('button', { name: '보내기' }).click();
  await expect(page.getByText('HTTP 200')).toBeVisible();
  await expect(page.getByText('success', { exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'd100' })).toBeVisible();
});

test('연결 모니터 — 커넥터 카드', async ({ page }) => {
  await openScreen(page, 'PLC', '연결 모니터');
  await expect(page.getByText(/게시 \d+ · 연결 \d+ · 끊김 \d+/)).toBeVisible();
  await expect(page.getByText('plc-e2e', { exact: true }).last()).toBeVisible();
});

test('인터페이스 — 등록(설정·점 표)·상세 보기·삭제', async ({ page }) => {
  const name = `e2e.ui.${Date.now()}`;
  await openScreen(page, 'PLC', '인터페이스');
  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('이름(opcode)').fill(name);
  await dialog.getByLabel('PLC 커넥터').click();
  await page.getByRole('option', { name: /^plc-e2e/ }).click();
  await dialog.getByRole('tab', { name: /프로토콜 설정/ }).click();
  await dialog.getByLabel('monitoringTimer').fill('32');
  await dialog.getByRole('tab', { name: /^점/ }).click();
  await dialog.getByLabel('point-name-0').fill('d100');
  await dialog.getByLabel('point-address-0').fill('D100');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  await searchFor(page, name);
  const row = page.getByRole('row').filter({ hasText: name }).first();
  await expect(row.getByText('d100=D100')).toBeVisible();

  // 상세 보기 — 점, 이 인터페이스의 설정, 요청 미리보기
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '상세 보기' }).click();
  const detail = page.getByRole('dialog');
  await expect(detail.getByRole('cell', { name: 'D100', exact: true }).first()).toBeVisible();
  await expect(detail.getByText('monitoringTimer=32').first()).toBeVisible();
  await expect(detail.getByRole('table', { name: 'request-plan' })).toBeVisible();
  await detail.getByRole('button', { name: /close|닫기/i }).first().click();

  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '삭제' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(0);
});

test('커넥터 — 발행 상태, 카드·표 보기, 잘못된 포트 셀 편집은 저장하지 않는다', async ({ page }) => {
  await openScreen(page, 'PLC', '커넥터');
  await expect(page.getByText('PLC 커넥터 목록')).toBeVisible();
  const row = page.getByRole('row').filter({ hasText: 'plc-e2e' });
  await expect(row.getByText('PUBLISHED')).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '작업' })).toBeVisible();

  // 셀 편집 — 잘못된 포트는 오류 알림, 값은 그대로(저장하지 않는다)
  const ports = row.locator('[data-field="ports"]');
  const before = (await ports.innerText()).trim();
  await ports.dblclick();
  const input = ports.locator('input');
  await input.fill('abc');
  await input.press('Enter');
  await expect(page.getByText('포트는 1~65535 숫자를 쉼표로 나눠 적습니다')).toBeVisible();
  await input.press('Escape');
  await expect(ports).toHaveText(before);

  // 카드 보기 — 카드에 발행 상태, 표로 되돌린다(이 브라우저에 기억되므로)
  await page.getByRole('button', { name: '카드로 보기' }).click();
  const card = page.getByLabel('connector-card-plc-e2e');
  await expect(card.getByText('PUBLISHED')).toBeVisible();
  await page.getByRole('button', { name: '표로 보기' }).click();
  await expect(row).toBeVisible();
});

test('인터페이스 — 송수신 이력, 등록 폼의 요청 미리보기(저장하지 않음)', async ({ page }) => {
  await openScreen(page, 'PLC', '인터페이스');
  await searchFor(page, 'plc.e2e.d100.read');
  const row = page.getByRole('row').filter({ hasText: 'plc.e2e.d100.read' }).first();
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '송수신 이력 보기' }).click();
  const history = page.getByRole('dialog');
  await expect(history.getByText('송수신 이력 — plc.e2e.d100.read')).toBeVisible();
  await expect(history.getByLabel('기간')).toBeVisible();
  await history.getByRole('button', { name: /close|닫기/i }).first().click();

  await page.getByRole('button', { name: '추가' }).click();
  const form = page.getByRole('dialog');
  await form.getByRole('tab', { name: /^점/ }).click();
  await form.getByLabel('point-name-0').fill('d100');
  await form.getByLabel('point-address-0').fill('D100');
  await form.getByRole('tab', { name: '요청 미리보기' }).click();
  await form.getByRole('button', { name: '미리보기' }).click();
  const plan = form.getByRole('table', { name: 'request-plan' });
  await expect(plan.getByRole('cell', { name: '0401/0000 일괄 읽기(워드)' })).toBeVisible();
  await expect(plan.getByRole('cell', { name: 'D100', exact: true })).toBeVisible();
  await form.getByRole('button', { name: /close|닫기/i }).first().click();
});
