import { expect, test, type Page } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * 운영 화면(5-5) — 송수신 기록·메시지 모니터·스케줄 Job·설정 변경 이력. 실제 PLC 읽기 요청(plc.e2e.d100.read)을 보낸다.
 * 만드는 스케줄 Job(e2e-ui-job-*)은 긴 간격으로 만들고 끝에 지운다.
 */
const RUN = Date.now();

test.beforeEach(async ({ page }) => {
  await login(page);
});

/** 로그인한 화면의 토큰으로 PLC 읽기 요청을 하나 보낸다(개발 서버가 /api 를 tc 로 넘긴다) */
async function sendRead(page: Page, transactionId: string): Promise<void> {
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem('wow-mes-session') ?? '{}').state?.token as string);
  const res = await page.request.post('/api/v1/ifsvc/request-and-reply', {
    headers: { Authorization: `Bearer ${token}` },
    data: { transactionId, messageId: transactionId, opcode: 'plc.e2e.d100.read', sender: 'e2e-ui', receiver: 'tc', data: {} },
  });
  expect(res.status()).toBe(200);
}

test('메시지 모니터 — 보낸 요청이 바로 보이고, 누르면 거래 기록', async ({ page }) => {
  await openScreen(page, '운영', '메시지 모니터');
  await expect(page.getByText(/실시간/)).toBeVisible();
  await page.waitForTimeout(1500); // SSE 구독이 서버에 등록될 때까지
  const trx = `ui-mon-${RUN}`;
  await sendRead(page, trx);
  const row = page.getByRole('row').filter({ hasText: trx }).filter({ visible: true });
  await expect(row.first()).toBeVisible({ timeout: 15_000 });
  await row.first().click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText(`거래 — ${trx}`)).toBeVisible();
  await expect(dialog.getByText('REQUEST', { exact: true })).toBeVisible();
  await expect(dialog.getByText('RESPONSE', { exact: true })).toBeVisible();
});

test('송수신 기록 — 기간·opcode 로 찾고 거래 보기', async ({ page }) => {
  const trx = `ui-audit-${RUN}`;
  await sendRead(page, trx);
  await openScreen(page, '운영', '송수신 기록');
  await page.getByRole('textbox', { name: '트랜잭션 ID' }).filter({ visible: true }).fill(trx);
  await page.getByRole('button', { name: '검색' }).filter({ visible: true }).click();
  const rows = page.getByRole('row').filter({ hasText: trx }).filter({ visible: true });
  await expect(rows).toHaveCount(2);
  await rows.first().click();
  await expect(page.getByRole('dialog').getByText(`거래 — ${trx}`)).toBeVisible();
});

test('스케줄 Job·설정 변경 이력 — PLC 요청 Job 등록·속성 탭·실행 이력 탭·멈춤·삭제, 이력에 남음', async ({ page }) => {
  const name = `e2e-ui-job-${RUN}`;
  await openScreen(page, '운영', '스케줄 Job');
  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('이름').fill(name);
  // 종류(PLC 요청)의 속성 interfaceName 이 표에 미리 들어가 있다
  await dialog.getByLabel('interfaceName 값').fill('plc.e2e.d100.read');
  await page.getByRole('option', { name: /^plc\.e2e\.d100\.read/ }).click();
  await dialog.getByLabel('주기').click();
  await page.getByRole('option', { name: '간격' }).click();
  await dialog.getByLabel('시작 지연(ms)').fill('0');
  await dialog.getByLabel('반복 간격(ms)').fill('600000');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  await searchFor(page, name);
  const row = page.getByRole('row').filter({ hasText: name }).filter({ visible: true }).first();
  await expect(row.getByText('PLC 요청')).toBeVisible();
  await expect(row.getByText('600s')).toBeVisible();
  await expect(row.getByText('NORMAL')).toBeVisible(); // 저장하면서 예약됐다

  // 행을 고르면 아래에 속성·실행 이력 탭 — 시작 지연 0 이라 한 번 돌았다
  await row.getByText(name).click();
  // interfaceName + auditMode(종류의 선택 속성, 기본 ALL)
  await expect(page.getByRole('tab', { name: /속성 \(2\)/ })).toBeVisible();
  await expect(page.getByLabel('interfaceName 값')).toHaveValue('plc.e2e.d100.read');
  await page.getByRole('tab', { name: '실행 이력' }).click();
  await page.getByRole('button', { name: '새로 고침' }).filter({ visible: true }).last().click();
  const histRow = page.getByRole('row').filter({ hasText: 'SUCCESS' }).filter({ hasText: 'plc.e2e.d100.read 성공' }).first();
  await expect(histRow).toBeVisible({ timeout: 15000 });
  await histRow.click();
  await expect(page.getByRole('button', { name: '송수신 기록 보기' })).toBeVisible();
  await page.keyboard.press('Escape');

  await row.getByRole('button', { name: '멈춤' }).or(row.getByRole('menuitem', { name: '멈춤' })).first().click();
  await expect(row.getByText('PAUSED')).toBeVisible();

  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '삭제' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: name }).filter({ visible: true })).toHaveCount(0);

  // 설정 변경 이력 — 이 Job 의 등록·삭제
  await openScreen(page, '운영', '설정 변경 이력');
  await page.getByLabel('대상 종류').filter({ visible: true }).click();
  await page.getByRole('option', { name: 'SCHEDULE_JOB', exact: true }).click();
  await page.getByRole('button', { name: '검색' }).filter({ visible: true }).click();
  const hist = page.getByRole('row').filter({ hasText: name }).filter({ visible: true });
  await expect(hist.filter({ hasText: 'CREATE' })).toHaveCount(1);
  await expect(hist.filter({ hasText: 'DELETE' })).toHaveCount(1);
  await hist.filter({ hasText: 'CREATE' }).first().click();
  await expect(page.getByRole('dialog').getByLabel('뒤')).toHaveValue(new RegExp(name));
});

test('스케줄 Job 실행 이력 — Job 이름·결과·최근 24시간으로 찾고 비고 보기', async ({ page }) => {
  const name = `e2e-ui-hist-${RUN}`;
  const token = await page.evaluate(() => JSON.parse(localStorage.getItem('wow-mes-session') ?? '{}').state?.token as string);
  const headers = { Authorization: `Bearer ${token}` };
  const M = '/api/v1/schedule-job/management';
  // 곧 한 번 도는 PLC 요청 Job(시작 지연 0, 긴 간격)을 만들고 예약한다
  expect((await page.request.post(`${M}/register`, { headers, data: {
    name, use: true, jobClassName: 'com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob', triggerType: 'simple', startDelay: 0, repeatInterval: 600000,
    description: 'e2e ui history', jobDetails: [{ entryKey: 'interfaceName', entryValue: 'plc.e2e.d100.read', entryValueType: 'string' }],
  } })).status()).toBe(200);
  await page.request.post(`/api/v1/schedule-job/monitoring/rescheduleJob/${name}`, { headers });
  try {
    await openScreen(page, '운영', '스케줄 Job 실행 이력');
    await searchFor(page, name);
    const search = page.getByRole('button', { name: '검색', exact: true }).filter({ visible: true });
    const row = page.getByRole('row').filter({ hasText: name }).filter({ visible: true });
    await expect(async () => {
      await search.click();
      await expect(row.first()).toBeVisible({ timeout: 2000 });
    }).toPass({ timeout: 20000 });
    await expect(row.first().getByText('SUCCESS')).toBeVisible();

    await page.getByRole('button', { name: '실패', exact: true }).click();
    await search.click();
    await expect(row).toHaveCount(0);
    await page.getByRole('button', { name: '성공', exact: true }).click();
    await page.getByLabel('최근 24시간').click();
    await expect(page.getByLabel('실행 시작')).toBeDisabled();
    await search.click();
    await expect(row.first()).toBeVisible();

    await row.first().click();
    await expect(page.getByRole('dialog').getByText(/plc\.e2e\.d100\.read 성공/)).toBeVisible();
    await expect(page.getByRole('button', { name: '송수신 기록 보기' })).toBeVisible();
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: '초기화' }).filter({ visible: true }).click();
    await expect(page.getByLabel('통합검색').filter({ visible: true })).toHaveValue('');
    await expect(page.getByLabel('실행 시작')).toBeEnabled();
  } finally {
    await page.request.delete(`${M}/delete/byName/${name}`, { headers });
  }
});
