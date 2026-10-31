import { apiClient } from 'wow-mes-frontend-shared';
import type { DashboardSummary } from '../types/dashboard';

/** 홈 요약 — GET /api/v1/dashboard/summary */
export async function fetchDashboardSummary(): Promise<DashboardSummary> {
  const res = await apiClient.get<DashboardSummary>('/v1/dashboard/summary');
  return res.data;
}
