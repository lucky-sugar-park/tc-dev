import { tcConfig } from '../../shared/tcConfig';
import type { ModbusDevice, ModbusTable, OpcUaInfo, OpcUaTag, OpcUaTagSpec, Vplc, VplcConnection, VplcKind, VplcMemory } from '../types/vplc';

const enc = encodeURIComponent;

/**
 * 가상 장비(vplc·vplc-modbus·vplc-opcua) — 화면이 장비를 바로 부른다(결정 F8, 주소는 tc-config.json). tc 의 토큰은 보내지 않는다 — 시험
 * 장비라 인증이 없다. 오류 본문 {message} 를 그대로 던진다.
 */
async function request<T>(baseUrl: string | undefined, method: string, path: string, body?: unknown): Promise<T> {
  const base = (baseUrl ?? '').replace(/\/$/, '');
  const res = await fetch(`${base}${path}`, {
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

const call = <T>(method: string, path: string, body?: unknown) => request<T>(tcConfig().vplcUrl, method, `/api/v1/vplc${path}`, body);

export type VplcControl = 'start' | 'stop' | 'pause' | 'resume';

export const vplcApi = {
  enabled: () => !!tcConfig().vplcUrl,
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
};

const modbus = <T>(method: string, path: string, body?: unknown) =>
  request<T>(tcConfig().vplcModbusUrl, method, `/api/v1/modbus/devices${path}`, body);

/** 가상 Modbus 장치(vplc-modbus, docs/virtual-devices.md §2) — 번호는 프로토콜 주소(0부터) */
export const modbusApi = {
  enabled: () => !!tcConfig().vplcModbusUrl,
  list: () => modbus<ModbusDevice[]>('GET', ''),
  add: (d: { name: string; port: number; unitId: number; size: number; delayMs: number }) => modbus<ModbusDevice>('POST', '', d),
  remove: (name: string) => modbus<void>('DELETE', `/${enc(name)}`),
  /** 비트 영역은 true/false, 레지스터는 0~65535 */
  read: (name: string, table: ModbusTable, start: number, count: number) =>
    modbus<{ start: number; values: (number | boolean)[] }>('GET', `/${enc(name)}/${table}?start=${start}&count=${count}`),
  write: (name: string, table: ModbusTable, start: number, values: (number | boolean)[]) =>
    modbus<{ start: number; values: (number | boolean)[] }>('PUT', `/${enc(name)}/${table}`, { start, values }),
  /** 늦게 답하기(ms)·답하지 않기 */
  behavior: (name: string, b: { delayMs?: number; silent?: boolean }) => modbus<ModbusDevice>('PUT', `/${enc(name)}/behavior`, b),
  dropConnections: (name: string) => modbus<{ port: number; dropped: number }>('POST', `/${enc(name)}/drop-connections`),
};

/** Modbus 영역 — 코일·입력 비트는 16개를 한 줄(워드)로 묶어 보인다. 입력 영역(IR·DI)도 화면에서는 바꿀 수 있다(장비 쪽 값 흉내) */
export const MODBUS_TABLES: { code: ModbusTable; label: string; bit: boolean }[] = [
  { code: 'hr', label: 'HR — Holding register', bit: false },
  { code: 'ir', label: 'IR — Input register', bit: false },
  { code: 'co', label: 'CO — Coil', bit: true },
  { code: 'di', label: 'DI — Discrete input', bit: true },
];

const opcua = <T>(method: string, path: string, body?: unknown) => request<T>(tcConfig().vplcOpcuaUrl, method, `/api/v1/opcua${path}`, body);

/** 가상 OPC UA 서버(vplc-opcua, docs/virtual-devices.md §3) — 태그 경로는 Line1.Temp 처럼 */
export const opcUaApi = {
  enabled: () => !!tcConfig().vplcOpcuaUrl,
  info: () => opcua<OpcUaInfo>('GET', ''),
  tags: () => opcua<OpcUaTag[]>('GET', '/tags'),
  add: (spec: OpcUaTagSpec) => opcua<OpcUaTag>('POST', '/tags', spec),
  set: (path: string, value: unknown) => opcua<OpcUaTag>('PUT', `/tags/${enc(path)}`, { value }),
  remove: (path: string) => opcua<void>('DELETE', `/tags/${enc(path)}`),
};

/** OPC UA 태그 형 — tc 점 형과 같은 이름 */
export const OPCUA_TYPES = ['BOOL', 'INT16', 'UINT16', 'INT32', 'UINT32', 'INT64', 'FLOAT32', 'FLOAT64', 'STRING'] as const;

/** 장비 종류 이름 */
export const KIND_LABEL: Record<VplcKind, string> = { MC: 'MELSEC MC', MODBUS: 'Modbus TCP', OPCUA: 'OPC UA' };

/** 가상 OPC UA 서버는 하나 — 목록·메모리 화면의 이름 */
export const OPCUA_NAME = 'vplc-opcua';

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
