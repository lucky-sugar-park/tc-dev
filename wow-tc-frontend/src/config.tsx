import type { ComponentType } from 'react';
import type { AppConfig, MenuNode, ScreenDef } from 'wow-mes-frontend-framework';
import { GROUPS, SCREENS, menuI18nCode, type ScreenMeta } from './screens';
import { ComingSoon } from './shared/components/ComingSoon';
import { TcLayout } from './layout/TcLayout';
import { tcConfig } from './shared/tcConfig';

type Loader = () => Promise<{ default: ComponentType }>;

/** 만든 화면 — 없는 키는 "준비 중" 화면(차례로 채운다, docs/frontend-design.md F10) */
const PAGES: Record<string, Loader> = {
  'tc.home': () => import('./home/pages/HomePage'),
  'tc.plc.connector': () => import('./plc/pages/ConnectorPage'),
  'tc.plc.interface': () => import('./plc/pages/InterfacePage'),
  'tc.plc.interface-test': () => import('./plc/pages/InterfaceTestPage'),
  'tc.plc.monitor': () => import('./plc/pages/ConnectionMonitorPage'),
};

const comingSoon = (m: ScreenMeta): Loader => () =>
  Promise.resolve({ default: () => <ComingSoon title={m.label} /> });

/** 가상 PLC 메뉴는 vplc 주소가 있을 때만(결정 F8) */
const visible = (m: { group?: string; key: string }): boolean =>
  !(m.group === 'tc.vplc' || m.key === 'tc.vplc') || !!tcConfig().vplcUrl;

function screens(): ScreenDef[] {
  return SCREENS.filter(visible).map((m) => ({
    key: m.key,
    path: m.path,
    load: PAGES[m.key] ?? comingSoon(m),
    menu: { label: m.label, i18nCode: menuI18nCode(m.key), icon: m.icon },
  }));
}

const leaf = (m: ScreenMeta): MenuNode => ({
  key: m.key,
  label: m.label,
  i18nCode: menuI18nCode(m.key),
  path: m.path,
  icon: m.icon,
  routeCode: `uiroute.${m.key}`,
});

/** 메뉴 — 정적(결정 U2). 1단계는 그룹, 2단계는 화면 */
function menu(): MenuNode[] {
  const home = SCREENS.find((s) => s.key === 'tc.home')!;
  return [
    { key: 'tc.main', label: home.label, i18nCode: menuI18nCode('tc.home'), icon: 'home', children: [leaf(home)] },
    ...GROUPS.filter((g) => visible({ key: g.key })).map((g) => ({
      key: g.key,
      label: g.label,
      i18nCode: menuI18nCode(g.key),
      icon: g.icon,
      children: SCREENS.filter((s) => s.group === g.key).map(leaf),
    })),
  ];
}

export function buildAppConfig(): AppConfig {
  return {
    screens: screens(),
    menu: menu(),
    // 권한은 서버(/api/me/permissions)가 정한다 — 셸이 덮어쓴다
    permissions: {},
    layout: TcLayout,
    // tc 에는 권한 요청 화면이 없다
    forbiddenRequestPath: null,
  };
}
