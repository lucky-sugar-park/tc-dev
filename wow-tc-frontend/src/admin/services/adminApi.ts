import { apiClient } from 'wow-mes-frontend-shared';
import type { ApiClient, ApiKey, CommonCode, I18nEntry, IssuedApiKey, TcUser, UserHistory, UserRole } from '../types/admin';

const enc = encodeURIComponent;

/** 사용자 — /api/v1/users (관리자) */
export const userApi = {
  async list(): Promise<TcUser[]> {
    return (await apiClient.get<TcUser[]>('/v1/users')).data;
  },
  async create(u: { id: string; name?: string; email?: string; userRoles: UserRole[]; initialPassword: string }): Promise<TcUser> {
    return (await apiClient.post<TcUser>('/v1/users', u)).data;
  },
  async update(id: string, u: { name?: string; email?: string; userRoles: UserRole[] }): Promise<TcUser> {
    return (await apiClient.put<TcUser>(`/v1/users/${enc(id)}`, u)).data;
  },
  async setEnabled(id: string, enabled: boolean): Promise<void> {
    await apiClient.post(`/v1/users/${enc(id)}/${enabled ? 'enable' : 'disable'}`);
  },
  async resetPassword(id: string, newPassword: string): Promise<void> {
    await apiClient.post(`/v1/users/${enc(id)}/password-reset`, { newPassword });
  },
  async remove(id: string): Promise<void> {
    await apiClient.delete(`/v1/users/${enc(id)}`);
  },
  async history(id: string, size = 200): Promise<UserHistory[]> {
    return (await apiClient.get<UserHistory[]>(`/v1/users/${enc(id)}/history`, { params: { size } })).data;
  },
  /** 실패가 많아 잠시 로그인을 받지 않는 아이디들 */
  async loginBlocked(): Promise<string[]> {
    return (await apiClient.get<string[]>('/v1/users/login-blocked')).data;
  },
  async unblock(id: string): Promise<boolean> {
    return (await apiClient.post<{ released: boolean }>(`/v1/users/${enc(id)}/login-unblock`)).data.released;
  },
};

/** API 클라이언트·키 — /api/v1/api-clients (관리자) */
export const apiClientApi = {
  async list(): Promise<ApiClient[]> {
    return (await apiClient.get<ApiClient[]>('/v1/api-clients')).data;
  },
  /** 한 개 — 키 목록 포함 */
  async find(clientId: string): Promise<ApiClient> {
    return (await apiClient.get<ApiClient>(`/v1/api-clients/${enc(clientId)}`)).data;
  },
  async register(c: ApiClient): Promise<ApiClient> {
    return (await apiClient.post<ApiClient>('/v1/api-clients', c)).data;
  },
  async update(c: ApiClient): Promise<ApiClient> {
    return (await apiClient.put<ApiClient>(`/v1/api-clients/${enc(c.clientId)}`, c)).data;
  },
  async setEnabled(clientId: string, enabled: boolean): Promise<void> {
    await apiClient.post(`/v1/api-clients/${enc(clientId)}/${enabled ? 'enable' : 'disable'}`);
  },
  async remove(clientId: string): Promise<void> {
    await apiClient.delete(`/v1/api-clients/${enc(clientId)}`);
  },
  /** 응답의 apiKey 는 이번 한 번만 보인다 */
  async issueKey(clientId: string, expiresInDays?: number): Promise<IssuedApiKey> {
    return (await apiClient.post<IssuedApiKey>(`/v1/api-clients/${enc(clientId)}/keys`, { expiresInDays: expiresInDays ?? null })).data;
  },
  async revokeKey(clientId: string, keyId: string): Promise<ApiKey> {
    return (await apiClient.post<ApiKey>(`/v1/api-clients/${enc(clientId)}/keys/${enc(keyId)}/revoke`)).data;
  },
};

/** 재기동 없이 바꾸는 보안 옵션 — /api/v1/security */
export const optionApi = {
  async permissionMode(): Promise<string> {
    return (await apiClient.get<{ mode: string }>('/v1/security/permission-mode')).data.mode;
  },
  /** null 이면 설정값으로 되돌린다 */
  async setPermissionMode(mode: string | null): Promise<string> {
    return (await apiClient.put<{ mode: string }>('/v1/security/permission-mode', { mode })).data.mode;
  },
  async dispatchAnyUser(): Promise<boolean> {
    return (await apiClient.get<{ on: boolean }>('/v1/security/dispatch-any-user')).data.on;
  },
  async setDispatchAnyUser(on: boolean | null): Promise<boolean> {
    return (await apiClient.put<{ on: boolean }>('/v1/security/dispatch-any-user', { on })).data.on;
  },
};

/** 다국어 관리 — /api/i18n/items */
export const i18nAdminApi = {
  async list(q?: string, kind?: string): Promise<I18nEntry[]> {
    return (await apiClient.get<I18nEntry[]>('/i18n/items', { params: { q: q || undefined, kind: kind || undefined } })).data;
  },
  async save(e: I18nEntry): Promise<I18nEntry> {
    return (await apiClient.put<I18nEntry>(`/i18n/items/${enc(e.code)}`, e)).data;
  },
  async remove(code: string): Promise<void> {
    await apiClient.delete(`/i18n/items/${enc(code)}`);
  },
};

/** 공통 코드 관리 — /api/common-codes */
export const commonCodeApi = {
  /** parent 가 없으면 그룹(맨 위) */
  async list(parent?: string): Promise<CommonCode[]> {
    return (await apiClient.get<CommonCode[]>('/common-codes', { params: { parent: parent || undefined } })).data;
  },
  async create(c: CommonCode): Promise<CommonCode> {
    return (await apiClient.post<CommonCode>('/common-codes', c)).data;
  },
  async update(c: CommonCode): Promise<CommonCode> {
    return (await apiClient.put<CommonCode>(`/common-codes/${enc(c.code)}`, c)).data;
  },
  async remove(code: string): Promise<void> {
    await apiClient.delete(`/common-codes/${enc(code)}`);
  },
};
