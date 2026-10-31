import { useCallback, useEffect, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Paper, Stack, Typography } from '@mui/material';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { CommandBarActions, GridTitle, PageContainer, ScreenAction, extractApiError, showToast, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { usePlcConnectionEvents, type PlcConnectionEvent } from '../hooks/usePlcConnectionEvents';
import { connectorApi } from '../services/plcApi';
import type { PlcConnectorInfo } from '../types/plc';
import { ConnectionChip } from './ConnectorPage';

const POLL_MS = 5_000;
const MAX_EVENTS = 200;

/**
 * 연결 모니터 (docs/frontend-design.md §5) — 모든 커넥터의 연결 상태 판(채널 수·연결 인스턴스·마지막 이벤트·오류)과 들어오는 연결 이벤트.
 * 이벤트(SSE plc_connection)는 PLC 를 연결한 인스턴스에서 나므로 상태 판은 5초마다 다시 읽어 다중화에서도 맞춘다.
 */
export default function ConnectionMonitorPage(): ReactElement {
  const { t } = useI18n();
  const active = useIsActiveScreen();
  const [connectors, setConnectors] = useState<PlcConnectorInfo[]>([]);
  const [events, setEvents] = useState<PlcConnectionEvent[]>([]);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      const [list, states] = await Promise.all([connectorApi.list(), connectorApi.states()]);
      const by = new Map(states.map((s) => [s.plcConnectorName, s]));
      setConnectors(list.map((c) => ({ ...c, connection: by.get(c.name) ?? null })));
      setUpdatedAt(Date.now());
    } catch (e) {
      showToast(extractApiError(e), 'error', { key: 'plc-monitor' });
    }
  }, []);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    if (!active) return undefined;
    void loadRef.current();
    const id = window.setInterval(() => { void loadRef.current(); }, POLL_MS);
    return () => window.clearInterval(id);
  }, [active]);

  usePlcConnectionEvents((e) => {
    setEvents((xs) => [e, ...xs].slice(0, MAX_EVENTS));
    void loadRef.current();
  }, active);

  const published = connectors.filter((c) => c.published);
  const up = published.filter((c) => (c.connection?.activeChannels ?? 0) > 0).length;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
      </CommandBarActions>
      <Typography variant="body2" color="text.secondary">
        {t('i18n.tc.monitor.summary', '게시 {p} · 연결 {u} · 끊김 {d}').replace('{p}', String(published.length)).replace('{u}', String(up)).replace('{d}', String(published.length - up))}
        {updatedAt && ` · ${t('i18n.tc.home.updatedAt', '갱신')} ${new Date(updatedAt).toLocaleTimeString()}`}
      </Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 1 }}>
        {connectors.map((c) => (
          <Paper key={c.name} variant="outlined" sx={{ p: 1.5, opacity: c.published ? 1 : 0.6 }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 0.5 }}>
              <Typography variant="subtitle2" sx={{ flex: 1 }} noWrap>{c.name}</Typography>
              <ConnectionChip info={c} />
            </Stack>
            <Typography variant="caption" component="div" color="text.secondary">
              {c.protocol} · {c.hostIp}:{(c.ports ?? []).join(',')}
            </Typography>
            {c.connection && (
              <>
                <Typography variant="caption" component="div">
                  {t('i18n.tc.connector.instance', '연결 인스턴스')}: {c.connection.instance ?? '-'}
                </Typography>
                <Typography variant="caption" component="div">
                  {c.connection.lastEvent ?? ''} {fmtTime(c.connection.lastEventAt)}
                </Typography>
                {c.connection.lastError && (
                  <Typography variant="caption" component="div" sx={{ color: 'error.main', wordBreak: 'break-all' }}>
                    {fmtTime(c.connection.lastErrorAt)} {c.connection.lastError}
                  </Typography>
                )}
              </>
            )}
          </Paper>
        ))}
      </Box>
      <GridTitle>{t('i18n.tc.monitor.events', '연결 이벤트(이 화면을 연 뒤)')}</GridTitle>
      <Paper variant="outlined" sx={{ flex: 1, minHeight: 120, overflow: 'auto', p: 1 }}>
        {events.length === 0 && <Typography variant="body2" color="text.secondary">{t('i18n.tc.monitor.noEvents', '아직 이벤트가 없습니다.')}</Typography>}
        {events.map((e, i) => (
          <Stack key={`${e.at}-${i}`} direction="row" spacing={1} sx={{ alignItems: 'center', py: 0.25 }}>
            <Typography variant="caption" sx={{ fontFamily: 'monospace', width: 90 }}>{new Date(e.at).toLocaleTimeString()}</Typography>
            <Chip size="small" label={e.type} color={e.type === 'CONNECT' ? 'success' : e.type === 'DISCONNECT' ? 'error' : 'default'} sx={{ width: 110 }} />
            <Typography variant="caption" sx={{ width: 150 }} noWrap>{e.name}</Typography>
            <Typography variant="caption" color="text.secondary" noWrap>
              {e.remote ?? ''} {e.cause ?? ''} · {t('i18n.tc.monitor.channels', '채널')} {e.activeChannels} · {e.instance ?? ''}
            </Typography>
          </Stack>
        ))}
      </Paper>
    </PageContainer>
  );
}
