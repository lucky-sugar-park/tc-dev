import { readFileSync } from 'node:fs';
import { expect, type Page } from '@playwright/test';

/** 시험 계정 — 아이디는 TC_UI_E2E_ID, 비밀번호는 파일(TC_UI_E2E_PASSWORD_FILE)에서 읽는다. 출력하지 않는다 */
export function credentials(): { id: string; password: string } {
  const id = process.env.TC_UI_E2E_ID ?? 'ui-demo';
  const file = process.env.TC_UI_E2E_PASSWORD_FILE;
  if (!file) throw new Error('TC_UI_E2E_PASSWORD_FILE 이 없습니다');
  return { id, password: readFileSync(file, 'utf-8').trim() };
}

/** 로그인 화면으로 들어가 셸과 홈이 뜰 때까지 */
export async function login(page: Page): Promise<void> {
  const { id, password } = credentials();
  await page.goto('/');
  await expect(page.getByText('WOW-TC').first()).toBeVisible();
  await page.getByLabel('아이디').fill(id);
  await page.getByLabel('비밀번호').fill(password);
  await page.getByRole('button', { name: '로그인' }).click();
  await expect(page.getByText('커넥터(게시)')).toBeVisible();
  // 셸이 권한·메뉴를 받고 홈 탭을 연 뒤에 움직인다(그 전에 연 화면이 홈으로 되돌려질 수 있다)
  await expect(page.getByRole('tab', { name: '홈', selected: true })).toBeVisible();
}

/** 메뉴로 화면을 연다 — 레일에서 그룹(아이콘, 이름은 툴팁), 패널에서 화면. 그 화면 탭이 선택될 때까지(한 번 더 시도) */
export async function openScreen(page: Page, group: string, label: string): Promise<void> {
  const selected = page.getByRole('tab', { name: label, selected: true });
  for (let attempt = 0; attempt < 2; attempt++) {
    await page.getByRole('button', { name: group, exact: true }).first().click();
    await page.getByRole('button', { name: label, exact: true }).first().click();
    try {
      await expect(selected).toBeVisible({ timeout: 5_000 });
      return;
    } catch {
      // 다시
    }
  }
  await expect(selected).toBeVisible();
}

/** 지금 보이는 화면의 통합검색 — 열린 탭마다 검색칸이 있어 보이는 것만 */
export async function searchFor(page: Page, text: string): Promise<void> {
  await page.getByLabel('통합검색').filter({ visible: true }).fill(text);
}

