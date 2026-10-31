/** 송수신 기록 한 줄 — 백엔드 ExchangedMessageAuditInfo */
export type AuditRow = {
  id: number;
  transactionId: string;
  messageId?: string;
  name: string;
  messageType: 'REQUEST' | 'RESPONSE' | 'ERROR' | 'DELIVERY' | string;
  timestamp: string | number;
  sender?: string;
  receiver?: string;
  dataMap?: Record<string, unknown>;
  description?: string;
  principal?: string;
};

export type AuditQuery = {
  from?: string;
  to?: string;
  opcode?: string;
  transactionId?: string;
  messageTypes?: string[];
  failedOnly?: boolean;
  principal?: string;
  page: number;
  size: number;
};

export type AuditPage = { items: AuditRow[]; total: number; page: number; size: number };

export type RedeliveryTarget = { deliveryType: string; targetName?: string | null; tableName?: string | null };

export type RedeliveryResult = { accepted: number; rejected: { auditId: number; target?: string; reason: string }[] };

export type JobDetail = { entryKey: string; entryValue: string; entryValueType?: string; oper?: string; scheduleJobId?: string; description?: string };

/** 스케줄 Job — 백엔드 ScheduleJobInfo */
export type ScheduleJob = {
  id?: string;
  name: string;
  schedulerName?: string;
  use: boolean;
  jobClassName: string;
  groupName?: string;
  triggerState?: string;
  stateChangedTime?: string;
  triggerType: 'cron' | 'simple';
  startDelay?: number | null;
  repeatInterval?: number | null;
  cronExpression?: string | null;
  jobDetails?: JobDetail[];
  description?: string;
};

export type JobHistory = {
  seqNo: number;
  jobName: string;
  executionTime?: string;
  executionResult?: string;
  nextFireTime?: string;
  remark?: string;
};

export type ConfigChange = {
  id: string;
  changedAt: string;
  actorType: string;
  actorId: string;
  actorIp?: string;
  targetType: string;
  targetId: string;
  targetName?: string;
  action: string;
  detail?: string;
  beforeJson?: string;
  afterJson?: string;
};
