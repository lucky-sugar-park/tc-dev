import { tcConfig } from '../../shared/tcConfig';
import type { RangePage, RangeParams } from '../../shared/components/RangeGrid';
import type {
  MemoryInfo, OpcUaInfo, OpcUaTag, OpcUaTagSpec, Vplc, VplcConnection, VplcConnectionHistory, VplcConnectionHistoryPage, VplcConnectionHistoryQuery,
  VplcProtocol,
} from '../types/vplc';

const enc = encodeURIComponent;

/**
 * 가상 PLC(vplc) — 화면이 vplc 를 바로 부른다(결정 F8, 주소는 tc-config.json 의 vplcUrl). 한 vplc 가 MELSEC·Modbus·OPC UA 가상 PLC 를
 * 여러 대 띄운다(docs/virtual-devices.md). tc 의 토큰은 보내지 않는다 — 시험 장비라 인증이 없다. 오류 본문 {errorCode, message} 의 message 를 그대로 던진다.
 */
async function call<T>(method: string, path: string, body?: unknown): Promise<T> {
  const base = (tcConfig().vplcUrl ?? '').replace(/\/$/, '');
  const res = await fetch(`${base}/api/v1/vplc${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`;
    try { msg = (JSON.parse(text) as { message?: string }).message ?? msg; } catch { /* 본문이 JSON 이 아니면 상태만 */ }
    throw new Error(msg);
  }
  return (text ? JSON.parse(text) : undefined) as T;
}

export type VplcControl = 'start' | 'stop' | 'pause' | 'resume';

export const vplcApi = {
  list: () => call<Vplc[]>('GET', '/management/search'),
  find: (id: string) => call<Vplc>('GET', `/management/find/${enc(id)}`),
  register: (v: Vplc) => call<void>('POST', '/management/register', v),
  update: (v: Vplc) => call<void>('POST', '/management/update', v),
  remove: (id: string) => call<void>('DELETE', `/management/delete/${enc(id)}`),
  publish: (id: string) => call<void>('POST', `/management/publish/${enc(id)}`),
  release: (id: string) => call<void>('POST', `/management/release/${enc(id)}`),
  connections: (id: string) => call<VplcConnection[]>('GET', `/management/search/connection/${enc(id)}`),
  /** 연결 이력 한 쪽 — 가상 PLC 이름·IP, 종류, 기간. 최신순 */
  connectionHistories: (q: VplcConnectionHistoryQuery) => call<VplcConnectionHistoryPage>('POST', '/management/connection-histories/query', q),
  /** 연결 이력 구간(docs/range-grid-design.md) — 조건은 connectionHistories 와 같고 page·size 는 쓰지 않는다 */
  connectionHistoryRange: (filter: Omit<VplcConnectionHistoryQuery, 'page' | 'size'>, range: RangeParams) =>
    call<RangePage<VplcConnectionHistory>>('POST', '/management/connection-histories/range', { filter, range }),
  control: (op: VplcControl, id: string) => call<Vplc>('POST', `/control/${op}/${enc(id)}`),
  /** 붙은 연결을 모두 끊는다(모든 종류) — tc 재연결 시험 */
  dropConnections: (id: string) => call<{ dropped: number }>('POST', `/control/drop-connections/${enc(id)}`),
  /** Modbus 응답 지연(ms) — 설정에도 남는다 */
  modbusDelay: (id: string, delayMs: number) => call<Vplc>('POST', `/control/delay/${enc(id)}`, { delayMs }),
  /** OPC UA 엔드포인트·네임스페이스 번호·세션 수 */
  opcUaInfo: (id: string) => call<OpcUaInfo>('GET', `/opcua/${enc(id)}`),

  // 메모리 — 종류와 관계없이 능력으로(docs/vplc-architecture.md §2.4)
  /** 능력 — 영역(MELSEC·Modbus) 또는 태그(OPC UA) */
  memory: (id: string) => call<MemoryInfo>('GET', `/memory/${enc(id)}`),
  /** 영역 값 — 단위 그대로(WORD 0~65535, BIT 0·1) */
  areaRead: (id: string, area: string, start: number, count: number) =>
    call<{ start: number; values: number[] }>('GET', `/memory/${enc(id)}/area/${enc(area)}?start=${start}&count=${count}`),
  /** WORD 는 -32768~65535, BIT 는 0·1 */
  areaWrite: (id: string, area: string, start: number, values: number[]) =>
    call<{ start: number; values: number[] }>('PUT', `/memory/${enc(id)}/area/${enc(area)}`, { start, values }),
  areaClear: (id: string, area: string) => call<void>('POST', `/memory/${enc(id)}/area/${enc(area)}/clear`),
  tags: (id: string) => call<OpcUaTag[]>('GET', `/memory/${enc(id)}/tags`),
  addTag: (id: string, spec: OpcUaTagSpec) => call<void>('POST', `/memory/${enc(id)}/tags`, spec),
  /** 지금 값 — 실행 중일 때만 */
  setTagValue: (id: string, path: string, value: unknown) => call<OpcUaTag>('PUT', `/memory/${enc(id)}/tags/${enc(path)}`, { value }),
  removeTag: (id: string, path: string) => call<void>('DELETE', `/memory/${enc(id)}/tags/${enc(path)}`),
};

/** OPC UA 태그 형 — tc 점 형과 같은 이름 */
export const OPCUA_TYPES = ['BOOL', 'INT16', 'UINT16', 'INT32', 'UINT32', 'INT64', 'FLOAT32', 'FLOAT64', 'STRING'] as const;

/** 종류 이름 */
export const PROTOCOL_LABEL: Record<VplcProtocol, string> = { MELSEC_MC: 'MELSEC MC', MODBUS_TCP: 'Modbus TCP', OPC_UA: 'OPC UA' };

/** 종류 칩 색 */
export const PROTOCOL_COLOR: Record<VplcProtocol, 'default' | 'primary' | 'secondary'> = { MELSEC_MC: 'default', MODBUS_TCP: 'primary', OPC_UA: 'secondary' };

/** 종류(예전 줄은 MELSEC_MC) */
export const protocolOf = (v: Pick<Vplc, 'protocol'>): VplcProtocol => v.protocol ?? 'MELSEC_MC';

/** MC 메모리 종류 — 등록 폼에서 고른다(vplc MelsecTypes.MemoryType 과 같은 코드). 화면의 주소·크기는 서버(GET /memory/{id})가 알려 준다 */
export const MEMORY_TYPES: { code: string; label: string; max: number; hex?: boolean; bit?: boolean }[] = [
  { code: 'D*', label: 'D — Data register', max: 12287 },
  { code: 'W*', label: 'W — Link register', max: 0x1FFF, hex: true },
  { code: 'R*', label: 'R — File register', max: 32767 },
  { code: 'ZR', label: 'ZR — File register', max: 0xFE7FF },
  { code: 'M*', label: 'M — Internal relay', max: 8191, bit: true },
  { code: 'L*', label: 'L — Latch relay', max: 8191, bit: true },
  { code: 'B*', label: 'B — Link relay', max: 0x1FFF, hex: true, bit: true },
  { code: 'X*', label: 'X — Input', max: 0x1FFF, hex: true, bit: true },
  { code: 'Y*', label: 'Y — Output', max: 0x1FFF, hex: true, bit: true },
  { code: 'F*', label: 'F — Annunciator', max: 2047, bit: true },
  { code: 'V*', label: 'V — Edge relay', max: 2047, bit: true },
  { code: 'S*', label: 'S — Step relay', max: 8191, bit: true },
  { code: 'SM', label: 'SM — Special relay', max: 2047, bit: true },
  { code: 'SD', label: 'SD — Special register', max: 2047 },
  { code: 'TN', label: 'TN — Timer current value', max: 2047 },
  { code: 'CN', label: 'CN — Counter current value', max: 1023 },
  { code: 'SW', label: 'SW — Link special register', max: 0x7FF, hex: true },
  { code: 'Z*', label: 'Z — Index register', max: 15 },
];


/** 상태 칩 색 */
export const STATUS_COLOR: Record<string, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
  RUNNING: 'success', PAUSED: 'warning', STOPPED: 'error', PUBLISHED: 'info', REGISTERED: 'default', RELEASED: 'default',
};

/** 메모리에 올라와 있는가(게시됨) — 시작·멈춤·메모리는 이때만 */
export const isPublished = (s?: string) => s === 'PUBLISHED' || s === 'RUNNING' || s === 'PAUSED' || s === 'STOPPED';
