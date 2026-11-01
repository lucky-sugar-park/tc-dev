import { expect, test, type Page } from '@playwright/test';
import { login, openScreen } from './helpers';

/**
 * 목록 화면의 번호 칸(첫 칸, 보이는 순서), 커넥터의 카드|표 토글 위치(필터 줄), 공통 코드의 왼쪽 접기·넓이 조절. 바꾸는 것은 없다.
 */
async function open(page: Page, group: string, label: string): Promise<void> {
  await openScreen(page, group, label);
  // 셸이 첫 로그인 뒤 늦게 홈 탭을 다시 고르는 일이 있다 — 화면 탭이 선택된 채로 머물 때까지
  const tab = page.getByRole('tab', { name: label, exact: true });
  let stable = 0;
  for (let i = 0; i < 15 && stable < 3; i++) {
    if ((await tab.getAttribute('aria-selected')) === 'true') stable += 1; else { stable = 0; await tab.click(); }
    await page.waitForTimeout(500);
  }
}

const grid = (page: Page) => page.locator('.MuiDataGrid-root').filter({ visible: true });

async function expectNumbered(page: Page, nth = 0): Promise<void> {
  const g = grid(page).nth(nth);
  // 첫 칸 머리글이 번호, 행이 있으면 첫 행은 1
  await expect(g.locator('[role="columnheader"]').first()).toHaveAttribute('data-field', '__rowNo');
  const first = g.locator('[role="gridcell"][data-field="__rowNo"]').first();
  if (await g.locator('[role="row"][data-rowindex="0"]').count()) await expect(first).toHaveText('1');
}

test.beforeEach(async ({ page }) => {
  await login(page);
});

for (const [group, label] of [['관리', '사용자'], ['관리', 'API 클라이언트'], ['관리', '다국어'], ['PLC', '인터페이스'],
  ['전달', '전달 정의'], ['전달', 'WebHook'], ['전달', '외부 DB 연결'], ['운영', '스케줄 Job'], ['가상 PLC', '가상 PLC 목록']] as const) {
  test(`${label} — 번호 칸`, async ({ page }) => {
    await open(page, group, label);
    await expectNumbered(page);
  });
}

test('메시지 모니터 — 번호 칸', async ({ page }) => {
  await open(page, '운영', '메시지 모니터');
  await expect(grid(page).locator('[role="columnheader"]').first()).toHaveAttribute('data-field', '__rowNo');
});

test('커넥터 — 카드|표 토글은 필터 줄(통합검색 오른쪽), 표에 번호 칸', async ({ page }) => {
  await open(page, 'PLC', '커넥터');
  const search = page.getByLabel('통합검색').filter({ visible: true });
  const tableBtn = page.getByRole('button', { name: '표로 보기' }).filter({ visible: true });
  const s = await search.boundingBox();
  const b = await tableBtn.boundingBox();
  expect(Math.abs((s?.y ?? 0) + (s?.height ?? 0) / 2 - ((b?.y ?? 0) + (b?.height ?? 0) / 2))).toBeLessThan(12); // 같은 줄
  expect(b!.x).toBeGreaterThan(s!.x + s!.width); // 오른쪽
  await tableBtn.click();
  await expectNumbered(page);
});

test('공통 코드 — 두 표에 번호, 왼쪽 넓이 조절·접기', async ({ page }) => {
  await open(page, '관리', '공통 코드');
  await expectNumbered(page, 0);
  await grid(page).nth(0).locator('[role="row"][data-rowindex="0"]').click();
  await expectNumbered(page, 1);

  const sep = page.getByRole('separator', { name: '왼쪽 넓이 조절' });
  const left = grid(page).nth(0);
  const before = (await left.boundingBox())!.width;
  const box = (await sep.boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + 20);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width / 2 + 120, box.y + 20, { steps: 8 });
  await page.mouse.up();
  await expect.poll(async () => (await left.boundingBox())!.width).toBeGreaterThan(before + 80);

  await page.getByRole('button', { name: '왼쪽 접기' }).click();
  await expect(grid(page)).toHaveCount(1); // 오른쪽 표만
  await page.getByRole('button', { name: '왼쪽 펴기' }).click();
  await expect(grid(page)).toHaveCount(2);
  if (process.env.TC_UI_SHOT_DIR) await page.screenshot({ path: process.env.TC_UI_SHOT_DIR + '/common-code.png' });
  // 다음 시험이 같은 넓이로 시작하게 되돌린다
  await page.evaluate(() => localStorage.removeItem('tc.commonCode.split'));
});
