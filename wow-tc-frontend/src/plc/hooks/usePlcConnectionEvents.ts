import { useEffect, useRef } from 'react';
import { serverEvents } from 'wow-mes-frontend-shared';

/** PLC 연결 이벤트(SSE {@code plc_connection}) — 연결·끊김마다. PLC 를 연결한 인스턴스에서만 오므로 화면은 주기 조회도 함께 한다 */
export type PlcConnectionEvent = {
  name: string;
  type: string;
  operStatus?: string;
  activeChannels: number;
  cause?: string;
  remote?: string;
  instance?: string;
  at: number;
};

/** 화면이 떠 있는 동안 연결 이벤트를 받는다(구독 권한은 서버가 본다 — tc.plc R) */
export function usePlcConnectionEvents(onEvent: (e: PlcConnectionEvent) => void, enabled = true): void {
  const ref = useRef(onEvent);
  ref.current = onEvent;
  useEffect(() => {
    if (!enabled) return undefined;
    return serverEvents.subscribe('plc_connection', (data: unknown) => {
      const e = (typeof data === 'string' ? JSON.parse(data) : data) as PlcConnectionEvent;
      ref.current(e);
    });
  }, [enabled]);
}
