import { expect, test } from '@playwright/test';
import { login, openScreen } from './helpers';

/**
 * 목록 표의 작업 칸 — 머리글은 "작업", 바로 보이는 아이콘과 ⋮(더보기)에는 이름이 툴팁으로 뜬다(StandardGrid·actionTooltips).
 * 줄이 없는 표는 머리글만 본다.
 */
const SCREENS: [string, string][] = [
  ['PLC', '커넥터'],
  ['PLC', '인터페이스'],
  ['전달', '전달 정의'],
  ['전달', 'WebHook'],
  ['전달', '외부 DB 연결'],
  ['운영', '스케줄 Job'],
  ['관리', '사용자'],
  ['관리', 'API 클라이언트'],
  ['관리', '다국어'],
  ['관리', '공통 코드'],
  ['가상 PLC', '가상 PLC 목록'],
];

test.beforeEach(async ({ page }) => {
  await login(page);
});

test('작업 칸 — 머리글 "작업", 아이콘 툴팁', async ({ page }) => {
  for (const [group, label] of SCREENS) {
    await openScreen(page, group, label);
    const grid = page.getByRole('grid').filter({ visible: true }).first();
    await expect(grid.getByRole('columnheader', { name: '작업' }).first(), label).toBeVisible();
    const buttons = grid.locator('.MuiDataGrid-actionsCell button');
    await expect(grid.locator('.MuiDataGrid-row').first().or(grid.getByText(/없습니다|No rows/).first())).toBeVisible();
    if ((await buttons.count()) === 0) continue;
    const first = buttons.first();
    const name = await first.getAttribute('aria-label');
    await first.hover();
    await expect(page.getByRole('tooltip').filter({ hasText: name ?? '' }), `${label} — ${name}`).toBeVisible();
    await page.mouse.move(0, 0);
    await expect(page.getByRole('tooltip')).toHaveCount(0);
    const more = grid.locator('.MuiDataGrid-actionsCell button[aria-haspopup="menu"]');
    if ((await more.count()) > 0) {
      await more.first().hover();
      await expect(page.getByRole('tooltip').filter({ hasText: '더보기' }), `${label} — ⋮`).toBeVisible();
      if (label === '가상 PLC 목록') await page.screenshot({ path: 'test-results/grid-actions-tooltip.png' });
      await page.mouse.move(0, 0);
    }
  }
});
