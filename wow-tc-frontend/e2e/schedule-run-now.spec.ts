import { expect, test } from '@playwright/test';
import { login, openScreen, searchFor } from './helpers';

/**
 * 스케줄 Job — 지금 실행(정리 Job, 기동 때 만드는 cleanup-schedule-job-exec-hist)과 PLC 요청 Job 의 auditMode 속성
 * (docs/data-retention.md §6). 등록 창은 저장하지 않고 닫는다.
 */
test.beforeEach(async ({ page }) => {
  await login(page);
});

test('스케줄 Job — 지금 실행, PLC 요청 Job 의 송수신 기록(auditMode) 속성', async ({ page }) => {
  await openScreen(page, '운영', '스케줄 Job');
  // 셸이 늦게 홈 탭을 다시 고르는 일이 있어 화면 탭을 한 번 더 고른다
  await page.getByRole('tab', { name: '스케줄 Job', exact: true }).click();
  const name = 'cleanup-schedule-job-exec-hist';
  await searchFor(page, name);
  const row = page.getByRole('row').filter({ hasText: name }).filter({ visible: true }).first();
  await row.locator('button[aria-haspopup="menu"]').click();
  await page.getByRole('menuitem', { name: '지금 실행' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /확인|OK/ }).last().click();
  await expect(page.getByText('실행을 요청했습니다')).toBeVisible();

  await page.getByRole('button', { name: '추가' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('auditMode')).toBeVisible();
  await expect(dialog.getByText('모두 남김')).toBeVisible();
  await dialog.getByText('모두 남김').click();
  await expect(page.getByRole('option', { name: '실패만 남김' })).toBeVisible();
  await page.keyboard.press('Escape');
});
