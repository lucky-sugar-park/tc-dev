/** 관리 화면(5-6) 모양 — 백엔드 UserInfo·ApiClientInfo·I18nEntry·CommonCodeInfo 와 같다 */

export type UserRole = 'ROLE_ADMIN' | 'ROLE_OPERATOR' | 'ROLE_VIEWER' | 'ROLE_MANAGER' | 'ROLE_EMPLOYEE' | 'ROLE_CLIENT' | 'ROLE_UNKNOWN';

export type TcUser = {
  id: string;
  name?: string;
  email?: string;
  userRoles: UserRole[];
  enabled: boolean;
  passwordChangeRequired?: boolean;
  passwordChangedAt?: string;
  lastLoginAt?: string;
  lastLoginIp?: string;
  createdDate?: string;
};

export type UserHistory = { seqNo: number; userId: string; oper: string; timestamp: string; ipAddress?: string; remark?: string };

export type ApiKey = {
  keyId: string;
  clientId: string;
  status: 'ACTIVE' | 'REVOKED';
  issuedAt?: string;
  expiresAt?: string;
  revokedAt?: string;
  lastUsedAt?: string;
  lastUsedIp?: string;
  expired?: boolean;
  unused?: boolean;
};

export type ApiClient = {
  clientId: string;
  name?: string;
  description?: string;
  enabled: boolean;
  allowedOpcodes: string[];
  allowedIps: string[];
  allowedPushUrlPrefixes: string[];
  rateLimitPerSec?: number | null;
  maxConcurrent?: number | null;
  keys?: ApiKey[] | null;
  activeKeyCount?: number;
  nearestExpiresAt?: string;
  lastUsedAt?: string;
};

export type IssuedApiKey = { clientId: string; keyId: string; apiKey: string; expiresAt?: string };

export type I18nEntry = {
  code: string;
  kind?: string;
  parent?: string;
  description?: string;
  use?: boolean;
  values: Record<string, string>;
};

export type CommonCode = {
  code: string;
  name?: string;
  i18nCode?: string;
  parent?: string | null;
  description?: string;
  isUse?: boolean;
};
