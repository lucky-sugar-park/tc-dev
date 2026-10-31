/** 가상 PLC(5-7) 모양 — vplc 의 VirtualPlcInfo·ConnectionInfo·MemoryInfo 와 같다 */

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
