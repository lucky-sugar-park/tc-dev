import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Paper, Stack, Typography } from '@mui/material';
import { DataGridPro, type GridColDef } from '@mui/x-data-grid-pro';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  CommandBarActions, GridTitle, LoadErrorState, PageContainer, ScreenAction, extractApiError, useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { fetchDashboardSummary } from '../services/dashboardApi';
import type { DashboardConnector, DashboardError, DashboardSummary } from '../types/dashboard';

/** 화면이 보이는 동안 다시 읽는 간격 */
const REFRESH_MS = 10_000;

const fmt = (ms?: number | string | null): string => {
  if (ms === null || ms === undefined || ms === '') return '';
  const d = new Date(typeof ms === 'string' ? Number(ms) : ms);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString();
};

function Tile({ label, value, tone }: { label: string; value: number | string; tone?: 'success' | 'error' | 'warning' }): ReactElement {
  return (
    <Paper variant="outlined" sx={{ p: 1.5, minWidth: 140, flex: '1 1 140px' }}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography variant="h5" sx={{ fontWeight: 700, color: tone ? `${tone}.main` : 'text.primary' }}>{value}</Typography>
    </Paper>
  );
}

/**
 * 홈 — 커넥터 연결 상태, 오늘 요청·실패 건수, 최근 오류(docs/frontend-design.md §5). 옛 화면의 템플릿 숫자 대신 실데이터.
 * 보이는 동안 10초마다 다시 읽는다.
 */
export default function HomePage(): ReactElement {
  const { t } = useI18n();
  const [data, setData] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const { error, fail, clear, retry } = useLoadFailure();
  const dataRef = useRef(data);
  dataRef.current = data;
  const active = useIsActiveScreen();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await fetchDashboardSummary());
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: dataRef.current !== null, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    if (!active) return undefined;
    void loadRef.current();
    const id = window.setInterval(() => { void loadRef.current(); }, REFRESH_MS);
    return () => window.clearInterval(id);
  }, [active]);

  const connectorCols = useMemo<GridColDef<DashboardConnector>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.connector.name', '커넥터'), flex: 1, minWidth: 140 },
    { field: 'protocol', headerName: t('i18n.tc.connector.protocol', '프로토콜'), width: 120 },
    {
      field: 'state', headerName: t('i18n.tc.connector.state', '연결 상태'), width: 130, sortable: false,
      renderCell: ({ row }) => {
        if (!row.published) return <Chip size="small" label={t('i18n.tc.connector.released', '해제')} />;
        const up = (row.connection?.activeChannels ?? 0) > 0;
        return <Chip size="small" color={up ? 'success' : 'error'}
          label={up ? `${t('i18n.tc.connector.connected', '연결')} ${row.connection?.activeChannels}` : t('i18n.tc.connector.disconnected', '끊김')} />;
      },
    },
    { field: 'instance', headerName: t('i18n.tc.connector.instance', '연결 인스턴스'), width: 200, valueGetter: (_v, row) => row.connection?.instance ?? '' },
    { field: 'lastError', headerName: t('i18n.tc.connector.lastError', '마지막 오류'), flex: 1.5, minWidth: 200, valueGetter: (_v, row) => row.connection?.lastError ?? '' },
  ], [t]);

  const errorCols = useMemo<GridColDef<DashboardError>[]>(() => [
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: number | null) => fmt(v) },
    { field: 'opcode', headerName: 'opcode', flex: 1, minWidth: 160 },
    { field: 'errorCode', headerName: t('i18n.tc.audit.errorCode', '오류 코드'), width: 220 },
    { field: 'errorMessage', headerName: t('i18n.tc.audit.errorMessage', '내용'), flex: 2, minWidth: 220 },
    { field: 'transactionId', headerName: t('i18n.tc.audit.transactionId', '트랜잭션 ID'), width: 200 },
  ], [t]);

  if (error && !data) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;
  const c = data?.connectors;
  const today = data?.today;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
      </CommandBarActions>
      <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
        <Tile label={t('i18n.tc.home.connectors', '커넥터(게시)')} value={c ? `${c.published} / ${c.total}` : '-'} />
        <Tile label={t('i18n.tc.home.connected', '연결됨')} value={c?.connected ?? '-'} tone="success" />
        <Tile label={t('i18n.tc.home.disconnected', '끊김')} value={c?.disconnected ?? '-'} tone={c && c.disconnected > 0 ? 'error' : undefined} />
        <Tile label={t('i18n.tc.home.requestsToday', '오늘 요청')} value={today?.requests ?? '-'} />
        <Tile label={t('i18n.tc.home.failuresToday', '오늘 실패')} value={today?.failures ?? '-'} tone={today && today.failures > 0 ? 'warning' : undefined} />
      </Stack>
      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', lg: 'row' }, gap: 1, flex: 1, minHeight: 0 }}>
        <Box sx={{ flex: 1, minWidth: 0, minHeight: 240, display: 'flex', flexDirection: 'column' }}>
          <GridTitle>{t('i18n.tc.home.connectorState', '커넥터 연결 상태')}</GridTitle>
          <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
            <Box sx={{ position: 'absolute', inset: 0 }}>
              <DataGridPro rows={c?.items ?? []} columns={connectorCols} getRowId={(r) => r.name} loading={loading && !data}
                density="compact" hideFooter disableRowSelectionOnClick />
            </Box>
          </Box>
        </Box>
        <Box sx={{ flex: 1, minWidth: 0, minHeight: 240, display: 'flex', flexDirection: 'column' }}>
          <GridTitle>{t('i18n.tc.home.recentErrors', '최근 오류')}</GridTitle>
          <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
            <Box sx={{ position: 'absolute', inset: 0 }}>
              <DataGridPro rows={data?.recentErrors ?? []} columns={errorCols} getRowId={(r) => `${r.transactionId}-${r.timestamp}-${r.kind}`}
                loading={loading && !data} density="compact" hideFooter disableRowSelectionOnClick />
            </Box>
          </Box>
        </Box>
      </Box>
      {data && (
        <Typography variant="caption" color="text.secondary">
          {t('i18n.tc.home.updatedAt', '갱신')} {fmt(data.generatedAt)}
        </Typography>
      )}
    </PageContainer>
  );
}
