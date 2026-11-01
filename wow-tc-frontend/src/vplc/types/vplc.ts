/** 가상 PLC(5-7) 모양 — vplc 의 VirtualPlcInfo·ConnectionInfo·MemoryInfo, vplc-modbus·vplc-opcua REST 와 같다 */

export type VplcStatus = 'REGISTERED' | 'PUBLISHED' | 'RUNNING' | 'PAUSED' | 'STOPPED' | 'RELEASED' | 'UNKNOWN';

export type ConnectedClient = { clientIp?: string; port: number; clientChannelId?: string; connTimestamp?: string };

export type VplcConnection = {
  vplcId: string;
  port: number;
  connectedCount: number;
  connected: boolean;
  connectedClientList?: ConnectedClient[];
};

export type Vplc = {
  id: string;
  name: string;
  status?: VplcStatus;
  frameFormat: 'BINARY' | 'ASCII';
  manufacturer?: string;
  ipAddress?: string;
  startPort: number;
  portCount: number;
  portConnLimit: number;
  description?: string;
  /** 메모리 종류 MC 코드(예: "D*", "M*", "ZR") */
  memoryTypeList: string[];
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

/** 가상 장비 종류 — MELSEC(vplc)·Modbus TCP(vplc-modbus)·OPC UA(vplc-opcua) */
export type VplcKind = 'MC' | 'MODBUS' | 'OPCUA';

export type ModbusTable = 'co' | 'di' | 'ir' | 'hr';

/** vplc-modbus 장치 — 장치 = 포트 + 단위 ID */
export type ModbusDevice = {
  name: string;
  bindAddress: string;
  port: number;
  unitId: number;
  /** 영역마다 칸 수 */
  size: number;
  delayMs: number;
  /** 답하지 않기(일시정지) */
  silent: boolean;
  /** 그 포트의 연결 수 */
  connections: number;
};

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
