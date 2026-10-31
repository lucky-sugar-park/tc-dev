/**
 * 화면 목록 — 키·경로·메뉴 이름(정적, docs/frontend-design.md §5, 결정 U2). 화면 키는 백엔드 {@code UiRoutePermissions} 와 같아야 한다 —
 * 권한 코드 {@code uiroute.<키>} 로 메뉴·화면이 가려진다.
 */
export type ScreenMeta = {
  key: string;
  path: string;
  label: string;
  /** 메뉴 1단계(그룹) 키 — 홈은 없음 */
  group?: string;
  icon: string;
};

export const GROUPS: { key: string; label: string; icon: string }[] = [
  { key: 'tc.plc', label: 'PLC', icon: 'plc' },
  { key: 'tc.delivery', label: '전달', icon: 'delivery' },
  { key: 'tc.ops', label: '운영', icon: 'monitoring' },
  { key: 'tc.admin', label: '관리', icon: 'admin' },
  { key: 'tc.vplc', label: '가상 PLC', icon: 'vplc' },
];

export const SCREENS: ScreenMeta[] = [
  { key: 'tc.home', path: '/', label: '홈', icon: 'home' },
  { key: 'tc.plc.connector', path: '/plc/connectors', label: '커넥터', group: 'tc.plc', icon: 'plc' },
  { key: 'tc.plc.interface', path: '/plc/interfaces', label: '인터페이스', group: 'tc.plc', icon: 'interface' },
  { key: 'tc.plc.interface-test', path: '/plc/interface-test', label: '인터페이스 시험', group: 'tc.plc', icon: 'test' },
  { key: 'tc.plc.monitor', path: '/plc/monitor', label: '연결 모니터', group: 'tc.plc', icon: 'monitoring' },
  { key: 'tc.delivery.rule', path: '/delivery/rules', label: '전달 정의', group: 'tc.delivery', icon: 'delivery' },
  { key: 'tc.delivery.webhook', path: '/delivery/webhooks', label: 'WebHook', group: 'tc.delivery', icon: 'webhook' },
  { key: 'tc.delivery.dbconn', path: '/delivery/db-connections', label: '외부 DB 연결', group: 'tc.delivery', icon: 'datasource' },
  { key: 'tc.ops.audit', path: '/ops/audit', label: '송수신 기록', group: 'tc.ops', icon: 'log' },
  { key: 'tc.ops.monitor', path: '/ops/monitor', label: '메시지 모니터', group: 'tc.ops', icon: 'analytics' },
  { key: 'tc.ops.schedule', path: '/ops/schedule-jobs', label: '스케줄 Job', group: 'tc.ops', icon: 'schedule' },
  { key: 'tc.ops.confighist', path: '/ops/config-history', label: '설정 변경 이력', group: 'tc.ops', icon: 'history' },
  { key: 'tc.admin.user', path: '/admin/users', label: '사용자', group: 'tc.admin', icon: 'users' },
  { key: 'tc.admin.apiclient', path: '/admin/api-clients', label: 'API 클라이언트', group: 'tc.admin', icon: 'key' },
  { key: 'tc.admin.option', path: '/admin/options', label: '운영 옵션', group: 'tc.admin', icon: 'settings' },
  { key: 'tc.admin.i18n', path: '/admin/i18n', label: '다국어', group: 'tc.admin', icon: 'translate' },
  { key: 'tc.admin.code', path: '/admin/common-codes', label: '공통 코드', group: 'tc.admin', icon: 'commonCode' },
  { key: 'tc.vplc.list', path: '/vplc/list', label: '가상 PLC 목록', group: 'tc.vplc', icon: 'vplc' },
  { key: 'tc.vplc.memory', path: '/vplc/memory', label: '메모리 보기·제어', group: 'tc.vplc', icon: 'memory' },
];

/** 다국어 코드 — 백엔드 씨앗 ui-seed/i18n/20-tc-menu.tsv 와 같다 */
export const menuI18nCode = (key: string): string => `i18n.menu.${key}`;
