import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Dialog, DialogContent, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { DataGridPro, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid-pro';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  CommandBarActions, DialogFooter, DialogTitleClose, DraggablePaper, FilterBar, LoadErrorState, PageContainer, ScreenAction, extractApiError,
  useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { configHistoryApi } from '../services/opsApi';
import type { ConfigChange } from '../types/ops';

const TARGET_TYPES = ['PLC_CONNECTOR', 'INTERFACE', 'WEBHOOK', 'DELIVERY', 'DB_CONNECTION', 'SCHEDULE_JOB', 'USER', 'API_CLIENT', 'API_KEY', 'OPTION', 'I18N', 'COMMON_CODE'];
const ACTION_COLOR: Record<string, 'success' | 'primary' | 'error' | 'warning' | 'default'> = {
  CREATE: 'success', UPDATE: 'primary', DELETE: 'error', CONTROL: 'warning',
};

const pretty = (s?: string) => {
  if (!s) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
};

/** 앞뒤 JSON 에서 값이 다른 맨 위 칸 */
function changedKeys(before?: string, after?: string): string[] {
  try {
    const a = before ? JSON.parse(before) as Record<string, unknown> : {};
    const b = after ? JSON.parse(after) as Record<string, unknown> : {};
    return [...new Set([...Object.keys(a), ...Object.keys(b)])].filter((k) => JSON.stringify(a[k]) !== JSON.stringify(b[k])
      && !['updatedDate', 'updatedDateAsString', 'createdDateAsString'].includes(k));
  } catch {
    return [];
  }
}

const toLocalInput = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);

/**
 * 설정 변경 이력 (docs/frontend-design.md §5) — 누가 언제 무엇을 바꿨는지(커넥터·인터페이스·전달·스케줄·사용자·API 키 …). 행을 누르면 앞뒤 값과 바뀐 칸.
 */
export default function ConfigHistoryPage(): ReactElement {
  const { t } = useI18n();
  const active = useIsActiveScreen();
  const [filter, setFilter] = useState({ from: toLocalInput(new Date(Date.now() - 7 * 86400_000)), to: '', targetType: '', targetId: '', actorId: '' });
  const [applied, setApplied] = useState(filter);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: 50 });
  const [rows, setRows] = useState<ConfigChange[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<ConfigChange | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const r = await configHistoryApi.search({
        from: applied.from ? new Date(applied.from).toISOString() : undefined,
        to: applied.to ? new Date(applied.to).toISOString() : undefined,
        targetType: applied.targetType || undefined, targetId: applied.targetId || undefined, actorId: applied.actorId || undefined,
        page: page.page, size: page.pageSize,
      });
      setRows(r.items);
      setTotal(r.total);
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [applied, page, fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { if (active) void loadRef.current(); }, [active, applied, page]);

  const columns = useMemo<GridColDef<ConfigChange>[]>(() => [
    { field: 'changedAt', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'action', headerName: t('i18n.tc.confighist.action', '동작'), width: 100,
      renderCell: ({ row }) => <Chip size="small" variant="outlined" color={ACTION_COLOR[row.action] ?? 'default'} label={row.action} />,
    },
    { field: 'targetType', headerName: t('i18n.tc.confighist.targetType', '대상 종류'), width: 140 },
    { field: 'targetName', headerName: t('i18n.tc.confighist.target', '대상'), width: 220, valueGetter: (_v, r) => r.targetName ?? r.targetId },
    { field: 'actorId', headerName: t('i18n.tc.confighist.actor', '바꾼 사람'), width: 140, valueGetter: (_v, r) => `${r.actorId} (${r.actorType})` },
    { field: 'actorIp', headerName: 'IP', width: 120 },
    { field: 'changed', headerName: t('i18n.tc.confighist.changed', '바뀐 칸'), flex: 1, minWidth: 200, sortable: false, valueGetter: (_v, r) => (r.action === 'UPDATE' ? changedKeys(r.beforeJson, r.afterJson).join(', ') : r.detail ?? '') },
  ], [t]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
      </CommandBarActions>
      <FilterBar onSearch={() => { setPage((p) => ({ ...p, page: 0 })); setApplied({ ...filter }); }}
        onReset={() => setFilter({ from: toLocalInput(new Date(Date.now() - 7 * 86400_000)), to: '', targetType: '', targetId: '', actorId: '' })}>
        <TextField size="small" type="datetime-local" label={t('i18n.tc.audit.from', '부터')} value={filter.from}
          onChange={(e) => setFilter({ ...filter, from: e.target.value })} slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.audit.from', '부터') }, inputLabel: { shrink: true } }} />
        <TextField size="small" type="datetime-local" label={t('i18n.tc.audit.to', '까지')} value={filter.to}
          onChange={(e) => setFilter({ ...filter, to: e.target.value })} slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.audit.to', '까지') }, inputLabel: { shrink: true } }} />
        <TextField select size="small" label={t('i18n.tc.confighist.targetType', '대상 종류')} value={filter.targetType} sx={{ width: 170 }}
          onChange={(e) => setFilter({ ...filter, targetType: e.target.value })} slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.confighist.targetType', '대상 종류') }, inputLabel: { shrink: true }, select: { displayEmpty: true } }}>
          <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
          {TARGET_TYPES.map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}
        </TextField>
        <TextField slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.confighist.targetId', '대상 ID') } }} size="small" label={t('i18n.tc.confighist.targetId', '대상 ID')} value={filter.targetId} onChange={(e) => setFilter({ ...filter, targetId: e.target.value })} />
        <TextField slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.confighist.actor', '바꾼 사람') } }} size="small" label={t('i18n.tc.confighist.actor', '바꾼 사람')} value={filter.actorId} onChange={(e) => setFilter({ ...filter, actorId: e.target.value })} />
      </FilterBar>
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={rows} columns={columns} getRowId={(r) => r.id} loading={loading} density="compact" showColumnVerticalBorder
            pagination paginationMode="server" rowCount={total} paginationModel={page} onPaginationModelChange={setPage} pageSizeOptions={[50, 100, 200]}
            disableRowSelectionOnClick onRowClick={(p) => setDetail(p.row as ConfigChange)}
            getRowClassName={(p) => (p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd')}
            sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }} />
        </Box>
      </Box>
      <Dialog open={!!detail} onClose={() => setDetail(null)} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
        <DialogTitleClose onClose={() => setDetail(null)}>{detail?.action} {detail?.targetType} — {detail?.targetName ?? detail?.targetId}</DialogTitleClose>
        <DialogContent dividers>
          {detail && (
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">
                {fmtTime(detail.changedAt)} · {detail.actorId} ({detail.actorType}) {detail.actorIp ?? ''} {detail.detail ? `· ${detail.detail}` : ''}
              </Typography>
              {detail.action === 'UPDATE' && (
                <Typography variant="body2">{t('i18n.tc.confighist.changed', '바뀐 칸')}: {changedKeys(detail.beforeJson, detail.afterJson).join(', ') || '-'}</Typography>
              )}
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 1 }}>
                <TextField label={t('i18n.tc.confighist.before', '앞')} multiline minRows={10} maxRows={30} value={pretty(detail.beforeJson)}
                  slotProps={{ input: { readOnly: true }, htmlInput: { 'aria-label': t('i18n.tc.confighist.before', '앞'), style: { fontFamily: 'monospace', fontSize: 12 } } }} />
                <TextField label={t('i18n.tc.confighist.after', '뒤')} multiline minRows={10} maxRows={30} value={pretty(detail.afterJson)}
                  slotProps={{ input: { readOnly: true }, htmlInput: { 'aria-label': t('i18n.tc.confighist.after', '뒤'), style: { fontFamily: 'monospace', fontSize: 12 } } }} />
              </Box>
            </Stack>
          )}
        </DialogContent>
        <DialogFooter onClose={() => setDetail(null)} />
      </Dialog>
    </PageContainer>
  );
}
