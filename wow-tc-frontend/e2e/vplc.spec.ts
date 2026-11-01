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
  await page.getByRole('menuitem', { name: item, exact: true }).click();
}

/** 장비를 고른다(메모리 화면) — 종류별로 묶인 선택 칸 */
async function pickDevice(page: Page, name: string) {
  await page.getByRole('combobox', { name: '가상 PLC' }).filter({ visible: true }).click();
  await page.getByRole('option', { name, exact: true }).click();
}

test('가상 PLC 목록 — 등록·게시·시작·연결·정지·해제·삭제', async ({ page }) => {
  const name = `e2e-ui-vplc-${RUN}`;
  const port = 15900 + (RUN % 50);
  await openScreen(page, '가상 PLC', '가상 PLC 목록');
  await expect(page.getByRole('row').filter({ hasText: 'vplc-e2e' }).filter({ visible: true }).getByText('RUNNING')).toBeVisible();

  await page.getByRole('button', { name: '추가', exact: true }).click();
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
  // 연결 클라이언트 보기 — 포트 번호·연결 수·클라이언트 IP 가로 표
  await rowMenu(page, name, '연결 클라이언트 보기');
  await expect(page.getByRole('dialog').getByRole('table', { name: 'connected-clients' }).getByRole('cell', { name: String(port), exact: true })).toHaveCount(1);
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
  await pickDevice(page, 'vplc-e2e');
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


test('가상 PLC 목록 — Modbus·OPC UA 가상 PLC, Modbus 등록·게시·시작·일시정지·재개·정지·해제·삭제', async ({ page }) => {
  const name = `e2e-ui-mb-${RUN}`;
  const port = 15950 + (RUN % 40);
  await openScreen(page, '가상 PLC', '가상 PLC 목록');
  const mb = page.getByRole('row').filter({ hasText: 'mb-e2e-2' }).filter({ visible: true }).first();
  await expect(mb).toContainText('Modbus TCP');
  await expect(mb).toContainText('단위 ID 2');
  const ua = page.getByRole('row').filter({ hasText: 'ua-e2e' }).filter({ visible: true }).first();
  await expect(ua).toContainText('OPC UA');
  await expect(ua).toContainText('opc.tcp://127.0.0.1:4840/vplc');
  await page.screenshot({ path: 'test-results/vplc-list.png' });

  // 등록 — 종류 Modbus TCP 를 고르면 단위 ID·칸 수·응답 지연 칸
  await page.getByRole('button', { name: '추가', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('combobox', { name: '종류', exact: true }).click();
  await page.getByRole('option', { name: 'Modbus TCP' }).click();
  await dialog.getByRole('textbox', { name: /^이름/ }).fill(name);
  await dialog.getByRole('spinbutton', { name: '포트', exact: true }).fill(String(port));
  await dialog.getByLabel('칸 수').fill('100');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  // MELSEC 과 같은 상태 단계
  await searchFor(page, name);
  const row = page.getByRole('row').filter({ hasText: name }).filter({ visible: true }).first();
  await expect(row.getByText('REGISTERED')).toBeVisible();
  await expect(row).toContainText('영역마다 100칸');
  await rowMenu(page, name, '게시');
  await expect(row.getByText('PUBLISHED')).toBeVisible();
  await row.getByRole('button', { name: '시작' }).click();
  await expect(row.getByText('RUNNING')).toBeVisible();
  await row.getByRole('button', { name: '일시정지' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /확인|OK/ }).last().click();
  await expect(row.getByText('PAUSED')).toBeVisible();
  await row.getByRole('button', { name: '재개' }).click();
  await expect(row.getByText('RUNNING')).toBeVisible();
  await rowMenu(page, name, '응답 지연');
  await page.getByRole('dialog').getByLabel('응답 지연(ms)').fill('20');
  await page.getByRole('dialog').getByRole('button', { name: '저장' }).click();
  await expect(row).toContainText('지연 20ms');
  await rowMenu(page, name, '정지');
  await expect(row.getByText('STOPPED')).toBeVisible();
  await rowMenu(page, name, '해제');
  await expect(row.getByText('RELEASED')).toBeVisible();
  await rowMenu(page, name, '삭제');
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: name }).filter({ visible: true })).toHaveCount(0);
});

test('가상 PLC 메모리 — Modbus 레지스터·코일·문자열(mb-e2e-2 의 900~ 를 쓰고 되돌림)', async ({ page }) => {
  await openScreen(page, '가상 PLC', '메모리 보기·제어');
  await pickDevice(page, 'mb-e2e-2');
  await expect(page.getByText('단위 ID 2').filter({ visible: true })).toBeVisible();
  const start = page.getByRole('spinbutton', { name: '시작 주소' }).filter({ visible: true });
  await start.fill('900');
  await start.press('Enter');
  await expect(page.getByText('HR900 ~ HR919')).toBeVisible();

  const dialog = page.getByRole('dialog');
  const writeWords = async (values: string) => {
    await page.getByTestId('command-bar-slot').getByRole('button', { name: '값 쓰기' }).click();
    await dialog.getByLabel('값(쉼표로 여러 개)').fill(values);
    await dialog.getByRole('button', { name: '값 쓰기' }).click();
    await expect(dialog).toBeHidden();
  };
  await writeWords('1234, -1, 0x00FF, 0, 0');
  const r900 = page.getByRole('row').filter({ hasText: 'HR900' });
  await expect(r900).toContainText('04D2');
  await expect(page.getByRole('row').filter({ hasText: 'HR901' })).toContainText('65535');
  await page.getByRole('row').filter({ hasText: 'HR903' }).getByRole('button', { name: 'b0 0' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'HR903' })).toContainText('0001');

  // 문자열 — Modbus 는 높은 바이트가 앞 글자: "AB" = 4142
  await page.getByTestId('command-bar-slot').getByRole('button', { name: '값 쓰기' }).click();
  await dialog.getByRole('button', { name: '문자열(ASCII)' }).click();
  await dialog.getByLabel('시작 주소').fill('904');
  await dialog.getByRole('textbox', { name: '문자열' }).fill('AB');
  await dialog.getByRole('button', { name: '값 쓰기' }).click();
  await expect(dialog).toBeHidden();
  const r904 = page.getByRole('row').filter({ hasText: 'HR904' });
  await expect(r904).toContainText('4142');
  await expect(r904).toContainText('AB');
  await writeWords('0,0,0,0,0');
  await expect(r900).toContainText('0000');

  // 코일 — 16개가 한 줄, 비트를 눌러 켜고 끈다(CO960 = 60번째 줄 b0)
  await page.getByRole('combobox', { name: '메모리' }).filter({ visible: true }).click();
  await page.getByRole('option', { name: 'CO', exact: true }).click();
  await start.fill('60');
  await start.press('Enter');
  await expect(page.getByText('CO960 ~ CO999')).toBeVisible(); // 칸 1000 — 끝에서 자른다
  const c960 = page.getByRole('row').filter({ hasText: 'CO960' });
  await c960.getByRole('button', { name: 'CO960 0' }).click();
  await expect(c960.getByRole('button', { name: 'CO960 1' })).toBeVisible();
  await page.screenshot({ path: 'test-results/vplc-memory-modbus.png' });
  await c960.getByRole('button', { name: 'CO960 1' }).click();
  await expect(c960.getByRole('button', { name: 'CO960 0' })).toBeVisible();
});

test('가상 PLC 메모리 — OPC UA 태그 추가·값 고치기·지우기', async ({ page }) => {
  const path = `E2eUi.T${RUN}`;
  await openScreen(page, '가상 PLC', '메모리 보기·제어');
  await pickDevice(page, 'ua-e2e');
  await expect(page.getByRole('row').filter({ hasText: 'Line1.Temp' })).toContainText('nsu=urn:wow:tc:vplc-opcua;s=Line1.Temp');

  await page.getByTestId('command-bar-slot').getByRole('button', { name: '태그 추가' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('textbox', { name: /^경로/ }).fill(path);
  await dialog.getByRole('combobox', { name: '형' }).click();
  await page.getByRole('option', { name: 'INT32', exact: true }).click();
  await dialog.getByLabel('처음 값').fill('5');
  await dialog.getByRole('button', { name: '저장' }).click();
  await expect(dialog).toBeHidden();

  const row = page.getByRole('row').filter({ hasText: path });
  await expect(row).toContainText('INT32');
  await row.getByRole('gridcell').nth(2).dblclick();
  await page.keyboard.press('ControlOrMeta+a');
  await page.keyboard.type('42');
  await page.keyboard.press('Enter');
  await expect(row.getByRole('gridcell').nth(2)).toHaveText('42');
  await page.screenshot({ path: 'test-results/vplc-memory-opcua.png' });

  await row.getByRole('button', { name: '삭제' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /확인|삭제|OK/ }).last().click();
  await expect(page.getByRole('row').filter({ hasText: path })).toHaveCount(0);
});
