/** 날짜(ISO 문자열·epoch ms) → 이 브라우저 시각. 비었거나 틀리면 빈 문자열 */
export function fmtTime(v?: string | number | null): string {
  if (v === null || v === undefined || v === '') return '';
  const d = new Date(typeof v === 'string' && /^\d+$/.test(v) ? Number(v) : v);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString();
}

/** 이름·설명 등에 검색어가 들었는가(대소문자 무시) */
export function matches(q: string, ...values: (string | null | undefined)[]): boolean {
  const needle = q.trim().toLowerCase();
  return !needle || values.some((v) => v != null && v.toLowerCase().includes(needle));
}

/**
 * 백엔드 검색 조건(framework Condition). pageInfo 를 보낼 때는 다섯 칸을 모두 보내야 한다 — 하나라도 빠지면 400.
 */
export function pageCondition(conditions: { name: string; condType: string; value: unknown }[], page: number, size: number,
  sortBy: string[], descending = true) {
  return {
    conditions,
    pageInfo: { page, size, sort: 0, sortDirection: descending ? 'DESCENDING' : 'ASCENDING', sortBy },
  };
}
