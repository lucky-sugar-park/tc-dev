/** 가상 PLC(5-7) 모양 — vplc 의 VirtualPlcInfo·ConnectionInfo·MemoryInfo·종류별 REST 와 같다(docs/virtual-devices.md) */

export type VplcStatus = 'REGISTERED' | 'PUBLISHED' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'RELEASED' | 'UNKNOWN';

export type ConnectedClient = { clientIp?: string; port: number; clientChannelId?: string; connTimestamp?: string };

export type VplcConnection = {
  vplcId: string;
  port: number;
  connectedCount: number;
  connected: boolean;
  connectedClientList?: ConnectedClient[];
};

/** 연결 이력 한 줄(CONNECTED_CLIENT_HIST) — 이름·종류는 지운 가상 PLC 면 비어 있다 */
export type VplcConnectionHistory = {
  seqNo: number;
  vplcId: string;
  vplcName?: string;
  protocol?: VplcProtocol;
  port: number;
  clientIp?: string;
  clientChannelId?: string;
  /** CONNECTED · DISCONNECTED · VPLC_RELEASED_BY_FORCED */
  connOrDisconn: string;
  timestamp: string;
  description?: string;
};

export type VplcConnectionHistoryQuery = { search?: string; protocol?: VplcProtocol; from?: string; to?: string; page: number; size: number };

export type VplcConnectionHistoryPage = { items: VplcConnectionHistory[]; total: number; page: number; size: number };

/** 가상 PLC 종류 — 한 vplc 가 세 종류를 여러 대 띄운다. tc 커넥터 프로토콜과 같은 이름 */
export type VplcProtocol = 'MELSEC_MC' | 'MODBUS_TCP' | 'OPC_UA';

export type Vplc = {
  id: string;
  name: string;
  /** 비어 있으면 MELSEC_MC(예전 줄) */
  protocol?: VplcProtocol;
  status?: VplcStatus;
  frameFormat: 'BINARY' | 'ASCII';
  manufacturer?: string;
  ipAddress?: string;
  startPort: number;
  portCount: number;
  portConnLimit: number;
  description?: string;
  /** 메모리 종류 MC 코드(예: "D*", "M*", "ZR") — Modbus 는 HR·IR·CO·DI, OPC UA 는 TAG(vplc 가 채운다) */
  memoryTypeList: string[];
  /** 종류별 설정 — MODBUS_TCP: unitId·size·delayMs, OPC_UA: path·requireLogin·trustAllClients */
  settings?: Record<string, unknown>;
  published?: boolean;
  connections?: VplcConnection[];
  updatedDate?: string;
};

/** 영역 메모리의 영역 하나(GET /memory/{id}) — MELSEC 메모리 종류·Modbus HR·IR·CO·DI */
export type MemoryArea = {
  /** 영역 코드(MELSEC D*, Modbus hr) */
  code: string;
  /** 표시(D, HR) */
  label: string;
  /** WORD 는 0~65535, BIT 는 0·1 */
  unit: 'WORD' | 'BIT';
  /** 칸 수(단위 기준) */
  size: number;
  /** 주소를 16진으로(MELSEC W·B·X·Y…) */
  hexAddress: boolean;
  /** 워드로 담지만 비트마다 주소(MELSEC M·X…) */
  bitDevice: boolean;
};

/** 메모리 능력 — 영역(MELSEC·Modbus) 또는 태그(OPC UA) */
export type MemoryInfo = { kind: 'AREA'; areas: MemoryArea[] } | { kind: 'TAG' };

export type OpcUaInfo = { endpointUrl: string; namespaceUri: string; namespaceIndex: number; tags: number; sessions: number };

export type OpcUaSimulation = { kind: 'RAMP' | 'SINE' | 'TOGGLE' | 'RANDOM'; periodMs?: number; min?: number; max?: number };

export type OpcUaTag = {
  path: string;
  nodeId: string;
  expandedNodeId: string;
  type: string;
  value: unknown;
  simulation?: OpcUaSimulation | null;
  description?: string | null;
};

export type OpcUaTagSpec = { path: string; type: string; value?: unknown; simulation?: OpcUaSimulation; description?: string };
