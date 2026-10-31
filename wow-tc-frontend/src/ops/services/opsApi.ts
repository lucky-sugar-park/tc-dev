import { apiClient } from 'wow-mes-frontend-shared';
import { pageCondition } from '../../shared/format';
import type {
  AuditPage, AuditQuery, ConfigChange, JobDetail, JobHistory, RedeliveryResult, RedeliveryTarget, ScheduleJob,
} from '../types/ops';

const enc = encodeURIComponent;

/** 송수신 기록 — 화면용 검색(기간 등)은 /search/query */
export const auditApi = {
  async query(q: AuditQuery): Promise<AuditPage> {
    return (await apiClient.post<AuditPage>('/v1/ifsvc/audit/search/query', q)).data;
  },
  /** 거래 하나의 모든 기록(요청·응답·오류·전달) */
  async transaction(transactionId: string): Promise<AuditPage> {
    return (await apiClient.post<AuditPage>('/v1/ifsvc/audit/search/query', { transactionId, page: 0, size: 200 })).data;
  },
};

/** 결과 재전송(관리자) — 202 accepted·rejected, 하나도 못 보내면 400(본문은 같다) */
export const redeliveryApi = {
  async deliveries(auditIds: number[]): Promise<RedeliveryResult> {
    return (await apiClient.post<RedeliveryResult>('/v1/ifsvc/redelivery/deliveries', { auditIds }, { validateStatus: (s) => s === 202 || s === 400 })).data;
  },
  async responses(auditIds: number[], targets: RedeliveryTarget[]): Promise<RedeliveryResult> {
    return (await apiClient.post<RedeliveryResult>('/v1/ifsvc/redelivery/responses', { auditIds, targets }, { validateStatus: (s) => s === 202 || s === 400 })).data;
  },
};

const J = '/v1/schedule-job/management';
const MON = '/v1/schedule-job/monitoring';

export const scheduleApi = {
  async list(): Promise<ScheduleJob[]> {
    return (await apiClient.post<ScheduleJob[]>(`${J}/search`, { conditions: [] })).data;
  },
  async details(name: string): Promise<JobDetail[]> {
    return (await apiClient.get<JobDetail[]>(`${J}/search/job-details/byName/${enc(name)}`)).data;
  },
  async register(job: ScheduleJob): Promise<void> {
    await apiClient.post(`${J}/register`, job);
  },
  async update(job: ScheduleJob): Promise<void> {
    await apiClient.post(`${J}/update`, job);
  },
  async remove(name: string): Promise<void> {
    await apiClient.delete(`${J}/delete/byName/${enc(name)}`);
  },
  /** pause·resume·unschedule(예약 취소)·reschedule(다시 예약) */
  async control(op: 'pauseScheduleJob' | 'resumeScheduleJob' | 'unscheduleJob' | 'rescheduleJob', name: string): Promise<void> {
    await apiClient.post(`${MON}/${op}/${enc(name)}`);
  },
  async history(name: string, page: number, size: number): Promise<JobHistory[]> {
    const body = pageCondition([{ name: 'jobName', condType: 'EQUALS', value: name }], page, size, ['seqNo']);
    return (await apiClient.post<JobHistory[]>(`${J}/search/job-exec-histories`, body)).data;
  },
};

export const configHistoryApi = {
  async search(q: { from?: string; to?: string; targetType?: string; targetId?: string; actorId?: string; page: number; size: number }):
      Promise<{ items: ConfigChange[]; total: number; page: number; size: number }> {
    return (await apiClient.post('/v1/config-history/search', q)).data;
  },
};
