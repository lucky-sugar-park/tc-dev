import { tcConfig } from '../../shared/tcConfig';
import type { ModbusTable, OpcUaInfo, OpcUaTag, OpcUaTagSpec, Vplc, VplcConnection, VplcMemory, VplcProtocol } from '../types/vplc';

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
  control: (op: VplcControl, id: string) => call<Vplc>('POST', `/control/${op}/${enc(id)}`),
  read: (v: Vplc, memoryType: string, startAddress: number, wordCount: number) =>
    call<VplcMemory>('POST', '/manipulation/read', { vplcId: v.id, memoryType, frameFormat: v.frameFormat, startAddress, wordCount }),
  /** 워드 값(-32768~65535) */
  writeWords: (v: Vplc, memoryType: string, startAddress: number, words: number[]) =>
    call<VplcMemory>('POST', '/manipulation/write', { vplcId: v.id, memoryType, frameFormat: v.frameFormat, startAddress, words }),
  /** 문자열(ASCII) — 한 워드에 두 글자, 낮은 바이트가 앞 글자 */
  writeText: (v: Vplc, memoryType: string, startAddress: number, text: string) =>
    call<VplcMemory>('POST', '/manipulation/write', {
      vplcId: v.id, memoryType, frameFormat: v.frameFormat, startAddress, wordCount: Math.ceil(text.length / 2), data: text,
    }),
  clearAll: (v: Vplc, memoryType: string, currentPosition: number) =>
    call<VplcMemory>('POST', '/manipulation/clear/all', { vplcId: v.id, memoryType, frameFormat: v.frameFormat, currentPosition }),
  /** 붙은 연결을 모두 끊는다(모든 종류) — tc 재연결 시험 */
  dropConnections: (id: string) => call<{ dropped: number }>('POST', `/control/drop-connections/${enc(id)}`),

  // Modbus — 번호는 프로토콜 주소(0부터)
  /** 비트 영역은 true/false, 레지스터는 0~65535 */
  modbusRead: (id: string, table: ModbusTable, start: number, count: number) =>
    call<{ start: number; values: (number | boolean)[] }>('GET', `/modbus/${enc(id)}/${table}?start=${start}&count=${count}`),
  modbusWrite: (id: string, table: ModbusTable, start: number, values: (number | boolean)[]) =>
    call<{ start: number; values: (number | boolean)[] }>('PUT', `/modbus/${enc(id)}/${table}`, { start, values }),
  /** 응답 지연(ms) — 설정에도 남는다 */
  modbusDelay: (id: string, delayMs: number) => call<Vplc>('POST', `/control/delay/${enc(id)}`, { delayMs }),

  // OPC UA — 태그 경로는 Line1.Temp 처럼
  opcUaInfo: (id: string) => call<OpcUaInfo>('GET', `/opcua/${enc(id)}`),
  opcUaTags: (id: string) => call<OpcUaTag[]>('GET', `/opcua/${enc(id)}/tags`),
  opcUaAddTag: (id: string, spec: OpcUaTagSpec) => call<void>('POST', `/opcua/${enc(id)}/tags`, spec),
  /** 지금 값 — 실행 중일 때만 */
  opcUaSetValue: (id: string, path: string, value: unknown) => call<OpcUaTag>('PUT', `/opcua/${enc(id)}/tags/${enc(path)}`, { value }),
  opcUaRemoveTag: (id: string, path: string) => call<void>('DELETE', `/opcua/${enc(id)}/tags/${enc(path)}`),
};

/** Modbus 영역 — 코일·입력 비트는 16개를 한 줄(워드)로 묶어 보인다. 입력 영역(IR·DI)도 화면에서는 바꿀 수 있다(장비 쪽 값 흉내) */
export const MODBUS_TABLES: { code: ModbusTable; label: string; bit: boolean }[] = [
  { code: 'hr', label: 'HR — Holding register', bit: false },
  { code: 'ir', label: 'IR — Input register', bit: false },
  { code: 'co', label: 'CO — Coil', bit: true },
  { code: 'di', label: 'DI — Discrete input', bit: true },
];

/** OPC UA 태그 형 — tc 점 형과 같은 이름 */
export const OPCUA_TYPES = ['BOOL', 'INT16', 'UINT16', 'INT32', 'UINT32', 'INT64', 'FLOAT32', 'FLOAT64', 'STRING'] as const;

/** 종류 이름 */
export const PROTOCOL_LABEL: Record<VplcProtocol, string> = { MELSEC_MC: 'MELSEC MC', MODBUS_TCP: 'Modbus TCP', OPC_UA: 'OPC UA' };

/** 종류(예전 줄은 MELSEC_MC) */
export const protocolOf = (v: Pick<Vplc, 'protocol'>): VplcProtocol => v.protocol ?? 'MELSEC_MC';

/** MC 메모리 종류 — vplc Types.MemoryType 과 같다(코드·이름·마지막 주소·16진 주소 여부) */
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

/** 주소 표기 — D100, W1A0(16진) */
export const addressLabel = (code: string, addr: number): string => {
  const m = MEMORY_TYPES.find((x) => x.code === code);
  return `${code.replace('*', '')}${m?.hex ? addr.toString(16).toUpperCase() : addr}`;
};

/** 상태 칩 색 */
export const STATUS_COLOR: Record<string, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
  RUNNING: 'success', PAUSED: 'warning', STOPPED: 'error', PUBLISHED: 'info', REGISTERED: 'default', RELEASED: 'default',
};

/** 메모리에 올라와 있는가(게시됨) — 시작·멈춤·메모리는 이때만 */
export const isPublished = (s?: string) => s === 'PUBLISHED' || s === 'RUNNING' || s === 'PAUSED' || s === 'STOPPED';
