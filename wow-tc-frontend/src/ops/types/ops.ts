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

/** 스케줄 Job 속성 한 줄(SCHEDULE_JOB_DETAIL) — Job 이 실행될 때 읽는다. 값 형은 string·int·long */
export type JobDetail = { entryKey: string; entryValue: string; entryValueType?: string; oper?: string; scheduleJobId?: string; description?: string };

/** 스케줄 Job — 백엔드 ScheduleJobInfo. last*·nextFireTime·recentFailCount·kind 는 목록 응답에만 */
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
  kind?: string;
  lastExecutionTime?: string | null;
  lastResult?: string | null;
  lastRemark?: string | null;
  lastInstanceId?: string | null;
  lastElapsedMs?: number | null;
  nextFireTime?: string | null;
  recentFailCount?: number | null;
};

/** 백엔드 ScheduleJobKind.PropertySpec */
export type JobPropertySpec = { key: string; valueType: string; required: boolean; defaultValue?: string | null; options: string[]; description?: string };

/** 등록할 수 있는 Job 종류 — GET /job-kinds */
export type JobKind = { kind: string; jobClassName: string; extraProperties: boolean; properties: JobPropertySpec[] };

export type JobHistory = {
  seqNo: number;
  jobName: string;
  executionTime?: string;
  executionResult?: string;
  nextFireTime?: string;
  instanceId?: string | null;
  elapsedMs?: number | null;
  remark?: string;
};

export type JobHistoryQuery = { jobName: string; from?: string; to?: string; result?: string; page: number; size: number };

export type JobHistoryPage = { items: JobHistory[]; total: number; page: number; size: number };

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
