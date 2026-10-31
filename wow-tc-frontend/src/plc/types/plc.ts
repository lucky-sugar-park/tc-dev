/** 연결 상태(Redis) — 백엔드 PlcConnectionState. 날짜는 ISO 문자열 */
export type PlcConnectionState = {
  plcConnectorName?: string;
  instance?: string;
  operStatus?: string;
  activeChannels: number;
  lastEvent?: string;
  lastEventAt?: string | null;
  lastConnectedAt?: string | null;
  lastError?: string | null;
  lastErrorAt?: string | null;
  updatedAt?: string | null;
};

export type PlcConnectorInfo = {
  id?: number;
  name: string;
  manufacturer?: string;
  adapterClassName?: string;
  published?: boolean;
  status1?: string;
  status2?: string;
  hostIp: string;
  ports: number[];
  connLimitPerPort?: number;
  connectionVips: string[];
  messageFrameFormat?: string;
  memTypes?: string[];
  protocol: string;
  settings: Record<string, unknown>;
  description?: string;
  connection?: PlcConnectionState | null;
  updatedBy?: string;
  updatedDate?: string;
};

export type ProtocolSetting = {
  key: string;
  type: 'NUMBER' | 'ENUM' | 'TEXT';
  defaultValue: unknown;
  min?: number | null;
  max?: number | null;
  options?: string[] | null;
  hex: boolean;
  description: string;
};

export type PointType = 'BOOL' | 'INT16' | 'UINT16' | 'INT32' | 'UINT32' | 'INT64' | 'FLOAT32' | 'FLOAT64' | 'STRING' | 'BYTES';

export const POINT_TYPES: PointType[] = ['BOOL', 'INT16', 'UINT16', 'INT32', 'UINT32', 'INT64', 'FLOAT32', 'FLOAT64', 'STRING', 'BYTES'];

export type ProtocolArea = { prefix: string; label: string; bit: boolean; hexNumber: boolean; readOnly: boolean; types: PointType[] };

export type ProtocolDescriptor = {
  protocol: string;
  label: string;
  settings: ProtocolSetting[];
  /** 인터페이스 settings 항목 — 커넥터 값 덮어쓰기와 인터페이스 전용(docs/interface-detail-design.md §3) */
  interfaceSettings: ProtocolSetting[];
  areas: ProtocolArea[];
  examples: string[];
};

export type PointInfo = {
  name: string;
  address: string;
  type: PointType;
  count?: number;
  length?: number;
  byteOrder?: 'BIG' | 'LITTLE' | null;
  wordOrder?: 'BIG' | 'LITTLE' | null;
  description?: string;
  order?: number;
};

export type PlcStatus = { protocol: string; code: number; text: string };

export type TestReadResult = { values: Record<string, unknown>; plcStatus: PlcStatus; elapsedMs: number };

export type ConnectionHistory = {
  id: number;
  plcConnectorName: string;
  eventType: string;
  cause?: string;
  timestamp?: string;
  description?: string;
  eventJson?: string;
};

export type InterfaceInfo = {
  id?: number;
  name: string;
  plcName?: string;
  interfaceType: 'READ' | 'WRITE';
  points?: PointInfo[];
  readBack?: boolean;
  /** 인터페이스 settings — 비면 커넥터 설정 그대로 */
  settings?: Record<string, unknown>;
  use?: boolean;
  description?: string;
  updatedBy?: string;
  updatedDate?: string;
};

/** 요청 미리보기의 요청 하나 — 보내지 않고 나눠 본 것(docs/interface-detail-design.md §7) */
export type PlannedRequest = {
  seq: number;
  function: string;
  target: string;
  count: number;
  unit: string;
  points: string[];
  frameHex: string;
};

export type RequestPlan = {
  protocol: string;
  plcName: string;
  settings: Record<string, unknown>;
  requests: PlannedRequest[];
  notes: string[];
};
