/** 연결 상태 — 백엔드 PlcConnectionState(Redis) */
export type PlcConnectionState = {
  instance?: string;
  operStatus?: string;
  activeChannels: number;
  lastEvent?: string;
  lastEventAt?: string;
  lastConnectedAt?: string;
  lastError?: string;
  lastErrorAt?: string;
};

export type DashboardConnector = {
  name: string;
  protocol: string;
  published: boolean;
  connection: PlcConnectionState | null;
};

export type DashboardError = {
  transactionId: string;
  opcode: string;
  timestamp: number | null;
  errorCode: string;
  errorMessage: string | null;
  kind: 'RESPONSE' | 'ERROR';
};

export type DashboardSummary = {
  connectors: { total: number; published: number; connected: number; disconnected: number; items: DashboardConnector[] };
  today: { from: number; requests: number; failures: number };
  recentErrors: DashboardError[];
  generatedAt: number;
};
