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
  areas: ProtocolArea[];
  examples: string[];
  rawSupported: boolean;
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

export type PropInfo = { name: string; order: number; type: string; length: number; strValue?: string; description?: string };

export type InterfaceInfo = {
  id?: number;
  name: string;
  plcName?: string;
  interfaceType: 'READ' | 'WRITE' | 'REPLY' | 'PUSH';
  mode?: 'RAW' | 'POINTS';
  points?: PointInfo[];
  readBack?: boolean;
  replyName?: string;
  headerProps?: PropInfo[];
  dataProps?: PropInfo[];
  dataLength?: number;
  use?: boolean;
  sync?: boolean;
  reply?: boolean;
  webHookNameList?: string[];
  description?: string;
  updatedBy?: string;
  updatedDate?: string;
};

export type PointsConversionInfo = {
  name: string;
  dryRun: boolean;
  applied: boolean;
  points: PointInfo[];
  warnings: string[];
  replyName?: string;
  replyUsedBy: string[];
};
