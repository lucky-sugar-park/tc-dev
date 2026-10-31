import { apiClient } from 'wow-mes-frontend-shared';
import type { DbConnectionInfo, DeliveryRule, WebHookInfo } from '../types/delivery';

const enc = encodeURIComponent;

/** 결과 전달 정의 — /api/v1/ifsvc/delivery */
export const deliveryApi = {
  async list(): Promise<DeliveryRule[]> {
    return (await apiClient.get<DeliveryRule[]>('/v1/ifsvc/delivery')).data;
  },
  async register(rule: DeliveryRule): Promise<DeliveryRule> {
    return (await apiClient.post<DeliveryRule>('/v1/ifsvc/delivery', rule)).data;
  },
  async update(id: string, rule: DeliveryRule): Promise<DeliveryRule> {
    return (await apiClient.put<DeliveryRule>(`/v1/ifsvc/delivery/${enc(id)}`, rule)).data;
  },
  async remove(id: string): Promise<void> {
    await apiClient.delete(`/v1/ifsvc/delivery/${enc(id)}`);
  },
};

/** WebHook — /api/v1/webhook */
export const webhookApi = {
  async list(): Promise<WebHookInfo[]> {
    return (await apiClient.get<WebHookInfo[]>('/v1/webhook/search/all')).data;
  },
  async register(h: WebHookInfo): Promise<void> {
    await apiClient.post('/v1/webhook/register', h);
  },
  async update(h: WebHookInfo): Promise<void> {
    await apiClient.post('/v1/webhook/update', h);
  },
  async remove(name: string): Promise<void> {
    await apiClient.delete(`/v1/webhook/delete/byName/${enc(name)}`);
  },
  /** 주소에 시험 데이터를 보내 본다 — {result: OK|FAIL, cause} */
  async check(url: string, sampleData: string): Promise<{ result: string; cause?: string }> {
    return (await apiClient.post('/v1/webhook/check-url', { url, sampleData })).data;
  },
};

/** 외부 DB 연결 — /api/v1/db-connections */
export const dbConnectionApi = {
  async list(): Promise<DbConnectionInfo[]> {
    return (await apiClient.get<DbConnectionInfo[]>('/v1/db-connections')).data;
  },
  async register(c: DbConnectionInfo): Promise<DbConnectionInfo> {
    return (await apiClient.post<DbConnectionInfo>('/v1/db-connections', c)).data;
  },
  async update(name: string, c: DbConnectionInfo): Promise<DbConnectionInfo> {
    return (await apiClient.put<DbConnectionInfo>(`/v1/db-connections/${enc(name)}`, c)).data;
  },
  async remove(name: string): Promise<void> {
    await apiClient.delete(`/v1/db-connections/${enc(name)}`);
  },
  async test(name: string): Promise<{ success: boolean; database?: string; message?: string }> {
    return (await apiClient.post(`/v1/db-connections/${enc(name)}/test`)).data;
  },
};
