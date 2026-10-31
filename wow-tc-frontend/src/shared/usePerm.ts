import { useCallback } from 'react';
import { can, useAppConfig, type AcrudAction } from 'wow-mes-frontend-framework';

/**
 * 화면 권한 — 서버가 준 {@code uiroute.<화면 키>} 의 동작 글자(C·R·U·D·X). 표의 행 버튼처럼 {@code ScreenAction} 이 아닌 곳에서 쓴다.
 * 서버도 같은 표로 막는다(숨기는 것은 편의).
 */
export function usePerm(screenKey: string): (action: AcrudAction) => boolean {
  const config = useAppConfig();
  const permissions = config?.permissions ?? {};
  return useCallback((action: AcrudAction) => can(permissions, `uiroute.${screenKey}`, action), [permissions, screenKey]);
}

/** 자원 권한 — 화면이 아닌 동작 자원(예 action.tc.redeliver)을 볼 때 */
export function useCan(resource: string, action: AcrudAction): boolean {
  const config = useAppConfig();
  return can(config?.permissions ?? {}, resource, action);
}
