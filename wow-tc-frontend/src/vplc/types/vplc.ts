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

export type WordInfo = { word: number; high: number; low: number; lhStr?: string; hlStr?: string };

export type VplcMemory = {
  vplcId: string;
  memoryType: string;
  startAddress: number;
  words: number[];
  wordInfos: WordInfo[];
};

export type ModbusTable = 'co' | 'di' | 'ir' | 'hr';

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
