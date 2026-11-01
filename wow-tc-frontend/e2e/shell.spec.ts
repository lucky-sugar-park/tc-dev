import { expect, test } from '@playwright/test';
import { login } from './helpers';

/**
 * 셸 — 왼쪽 1단계 메뉴 순서와 tc 설정 창(시스템 탭·챗봇 도우미 없음, 화면 배치 미리보기는 WOW-TC). 바꾸는 것은 없다.
 */
test.beforeEach(async ({ page }) => {
  await login(page);
});

test('메뉴 레일 — 홈, 관리, PLC, 전달, 운영, 가상 PLC 순서', async ({ page }) => {
  const order = ['홈', '관리', 'PLC', '전달', '운영', '가상 PLC'];
  const ys: number[] = [];
  for (const name of order) {
    const box = await page.getByRole('button', { name, exact: true }).first().boundingBox();
    expect(box, name).not.toBeNull();
    ys.push(box!.y);
  }
  expect(ys).toEqual([...ys].sort((a, b) => a - b));
});

test('설정 — 시스템 탭·챗봇 도우미 없음, 화면 배치 미리보기는 WOW-TC', async ({ page }) => {
  await page.getByRole('button', { name: '설정', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('테마/모양').first()).toBeVisible();
  await expect(dialog.getByRole('tab', { name: '시스템' })).toHaveCount(0);
  await expect(dialog.getByText('챗봇 도우미')).toHaveCount(0);

  await dialog.getByRole('button', { name: '화면 배치' }).click();
  const preview = dialog.getByLabel('layout-preview');
  await expect(preview.getByText('WOW-TC')).toBeVisible();
  await expect(preview.getByText(/PLC › 인터페이스/)).toBeVisible();
  await expect(dialog.getByText('WOW-MES')).toHaveCount(0);
  await expect(dialog.getByText('발주관리')).toHaveCount(0);
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/settings-layout.png' });
});
