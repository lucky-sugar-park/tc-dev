import { apiClient } from 'wow-mes-frontend-shared';
import type { RangePage, RangeParams } from '../../shared/components/RangeGrid';
import type {
  AuditPage, AuditQuery, AuditRow, ConfigChange, JobDetail, JobHistory, JobHistoryPage, JobHistoryQuery, JobKind, RedeliveryResult,
  RedeliveryTarget, ScheduleJob,
} from '../types/ops';

const enc = encodeURIComponent;

/** 송수신 기록 — 화면용 검색(기간 등)은 /search/query */
export const auditApi = {
  async query(q: AuditQuery): Promise<AuditPage> {
    return (await apiClient.post<AuditPage>('/v1/ifsvc/audit/search/query', q)).data;
  },
  /** 구간 조회(docs/range-grid-design.md) — 목록에는 본문(dataMap)이 빠진다. 엑셀에서 본문을 고르면 includeBody */
  async range(filter: Omit<AuditQuery, 'page' | 'size'>, range: RangeParams, extra?: Record<string, boolean>): Promise<RangePage<AuditRow>> {
    return (await apiClient.post<RangePage<AuditRow>>('/v1/ifsvc/audit/search/query/range', { filter, range, includeBody: !!extra?.includeBody })).data;
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
  /** 지금 한 번 실행 — 주기는 그대로, 결과는 실행 이력에. 예약되지 않은 Job 은 409 */
  async runNow(name: string): Promise<void> {
    await apiClient.post(`${MON}/runScheduleJobNow/${enc(name)}`);
  },
  /** 실행 이력 한 쪽 — 최신순. 결과·기간은 선택 */
  async history(q: JobHistoryQuery): Promise<JobHistoryPage> {
    return (await apiClient.post<JobHistoryPage>(`${J}/exec-histories/query`, q)).data;
  },
  /** 실행 이력 구간(docs/range-grid-design.md) — 조건은 history 와 같고 page·size 는 쓰지 않는다 */
  async historyRange(filter: Omit<JobHistoryQuery, 'page' | 'size'>, range: RangeParams): Promise<RangePage<JobHistory>> {
    return (await apiClient.post<RangePage<JobHistory>>(`${J}/exec-histories/range`, { filter, range })).data;
  },
  /** 등록할 수 있는 Job 종류와 속성 정의 */
  async kinds(): Promise<JobKind[]> {
    return (await apiClient.get<JobKind[]>(`${J}/job-kinds`)).data;
  },
};

export const configHistoryApi = {
  async search(q: { from?: string; to?: string; targetType?: string; targetId?: string; actorId?: string; page: number; size: number }):
      Promise<{ items: ConfigChange[]; total: number; page: number; size: number }> {
    return (await apiClient.post('/v1/config-history/search', q)).data;
  },
  /** 구간 조회(docs/range-grid-design.md) — 스냅샷은 시각 */
  async range(filter: ConfigHistoryFilter, range: RangeParams): Promise<RangePage<ConfigChange>> {
    return (await apiClient.post<RangePage<ConfigChange>>('/v1/config-history/range', { filter, range })).data;
  },
};

export type ConfigHistoryFilter = { from?: string; to?: string; targetType?: string; targetId?: string; actorId?: string };
