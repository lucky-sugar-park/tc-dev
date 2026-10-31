import { apiClient } from 'wow-mes-frontend-shared';
import { pageCondition } from '../../shared/format';
import type {
  ConnectionHistory, InterfaceInfo, PlcConnectionState, PlcConnectorInfo, PointInfo, PointsConversionInfo, ProtocolDescriptor, RequestPlan, TestReadResult,
} from '../types/plc';

const C = '/v1/plc-connector';
const M = '/v1/ifsvc/management';
const enc = encodeURIComponent;

// ── 커넥터 ──

export const connectorApi = {
  async list(): Promise<PlcConnectorInfo[]> {
    return (await apiClient.get<PlcConnectorInfo[]>(`${C}/search/all`)).data;
  },
  async find(name: string): Promise<PlcConnectorInfo> {
    return (await apiClient.get<PlcConnectorInfo>(`${C}/find/byName/${enc(name)}`)).data;
  },
  async register(info: PlcConnectorInfo): Promise<void> {
    await apiClient.post(`${C}/register`, info);
  },
  async update(info: PlcConnectorInfo): Promise<void> {
    await apiClient.post(`${C}/update`, info);
  },
  async remove(name: string): Promise<void> {
    await apiClient.delete(`${C}/delete/byName/${enc(name)}`);
  },
  /** publish·release·pause·resume */
  async control(op: 'publish' | 'release' | 'pause' | 'resume', name: string): Promise<boolean> {
    return (await apiClient.post<{ success: boolean }>(`${C}/control/${op}/byName/${enc(name)}`)).data.success;
  },
  async testConnection(name: string): Promise<Record<string, string>> {
    return (await apiClient.post<Record<string, string>>(`${C}/test-connection/byName/${enc(name)}`)).data;
  },
  async testRead(name: string, points: PointInfo[]): Promise<TestReadResult> {
    return (await apiClient.post<TestReadResult>(`${C}/test-read/byName/${enc(name)}`, { points })).data;
  },
  async states(): Promise<PlcConnectionState[]> {
    return (await apiClient.get<PlcConnectionState[]>(`${C}/connection-states`)).data;
  },
  async history(name: string, page: number, size: number): Promise<{ rows: ConnectionHistory[]; totalRowsCount: number }> {
    const body = pageCondition([{ name: 'plcConnectorName', condType: 'EQUALS', value: name }], page, size, ['timestamp']);
    return (await apiClient.post(`${C}/search/connection-history`, body)).data;
  },
};

let protocolsCache: Promise<ProtocolDescriptor[]> | null = null;

/** 프로토콜 설명 — 바뀔 일이 없어 세션 동안 한 번 받는다 */
export function fetchProtocols(): Promise<ProtocolDescriptor[]> {
  protocolsCache ??= apiClient.get<ProtocolDescriptor[]>(`${C}/protocols`).then((r) => r.data).catch((e) => { protocolsCache = null; throw e; });
  return protocolsCache;
}

// ── 인터페이스 ──

export const interfaceApi = {
  async list(): Promise<InterfaceInfo[]> {
    return (await apiClient.get<InterfaceInfo[]>(`${M}/search`)).data;
  },
  async find(name: string): Promise<InterfaceInfo> {
    return (await apiClient.get<InterfaceInfo>(`${M}/find/byName/${enc(name)}`)).data;
  },
  async register(info: InterfaceInfo): Promise<void> {
    await apiClient.post(`${M}/register`, info);
  },
  async update(info: InterfaceInfo): Promise<void> {
    await apiClient.post(`${M}/update`, info);
  },
  async remove(name: string): Promise<void> {
    await apiClient.delete(`${M}/delete/byName/${enc(name)}`);
  },
  async setUse(id: number, use: boolean): Promise<void> {
    await apiClient.post(`${M}/update/apply-use/${id}/${use}`);
  },
  async convert(name: string, dryRun: boolean): Promise<PointsConversionInfo> {
    return (await apiClient.post<PointsConversionInfo>(`${M}/convert-to-points/byName/${enc(name)}?dryRun=${dryRun}`)).data;
  },
  /** 요청 미리보기 — 저장 전 정의도 된다. data 는 쓰기 값(없는 점은 0) */
  async plan(interfaceInfo: InterfaceInfo, data?: Record<string, unknown>): Promise<RequestPlan> {
    return (await apiClient.post<RequestPlan>(`${M}/plan`, { interfaceInfo, data })).data;
  },
};

export async function fetchWebhookNames(): Promise<string[]> {
  const rows = (await apiClient.get<{ name: string }[]>('/v1/webhook/search/all')).data;
  return rows.map((r) => r.name).sort();
}

// ── PLC 요청(인터페이스 시험) ──

export type TcResponse = {
  success?: boolean;
  data?: Record<string, unknown>;
  errorCode?: string;
  errorMessage?: string;
  plcStatus?: { protocol: string; code: number; text: string };
  resultCode?: number;
  resultDescription?: string;
  changed?: boolean;
  [k: string]: unknown;
};

/** POST /api/v1/ifsvc/request-and-reply(동기) 또는 /request(접수만). 실패도 본문을 돌려준다(HTTP 상태와 함께) */
export async function sendPlcRequest(opcode: string, data: Record<string, unknown>, reply: boolean):
    Promise<{ status: number; body: TcResponse; elapsedMs: number; transactionId: string }> {
  const transactionId = `ui-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const t0 = performance.now();
  const res = await apiClient.post<TcResponse>(reply ? '/v1/ifsvc/request-and-reply' : '/v1/ifsvc/request', {
    transactionId, messageId: transactionId, opcode, sender: 'tc-ui', receiver: 'tc', data,
  }, { validateStatus: () => true });
  return { status: res.status, body: res.data, elapsedMs: Math.round(performance.now() - t0), transactionId };
}
