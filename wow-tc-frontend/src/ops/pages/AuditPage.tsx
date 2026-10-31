import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Checkbox, Chip, FormControlLabel, MenuItem, TextField, Typography } from '@mui/material';
import { DataGridPro, type GridColDef, type GridPaginationModel, type GridRowSelectionModel } from '@mui/x-data-grid-pro';
import ReplayIcon from '@mui/icons-material/Replay';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  CommandBarActions, FilterBar, LoadErrorState, PageContainer, ScreenAction, extractApiError, useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { useCan } from '../../shared/usePerm';
import { AuditDetailDialog, TYPE_COLOR, isFailed } from '../components/AuditDetailDialog';
import { RedeliverDialog } from '../components/RedeliverDialog';
import { auditApi } from '../services/opsApi';
import type { AuditRow } from '../types/ops';

const TYPES = ['REQUEST', 'RESPONSE', 'ERROR', 'DELIVERY'];

/** datetime-local 값(이 브라우저 시각) ↔ ISO */
const toLocalInput = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
const fromLocalInput = (s: string) => (s ? new Date(s).toISOString() : undefined);

type Filter = { from: string; to: string; opcode: string; transactionId: string; type: string; failedOnly: boolean };

const initialFilter = (): Filter => ({
  from: toLocalInput(new Date(Date.now() - 3600_000)), to: '', opcode: '', transactionId: '', type: '', failedOnly: false,
});

/**
 * 송수신 기록 (docs/frontend-design.md §5·§14) — 기간·opcode·거래 ID·종류·실패만으로 찾는다(서버 쪽 단위, 최신순). 행을 누르면 그 거래의
 * 기록 전부(요청·응답·오류·전달). 관리자는 골라서 결과 재전송.
 */
export default function AuditPage(): ReactElement {
  const { t } = useI18n();
  const active = useIsActiveScreen();
  const canRedeliver = useCan('action.tc.redeliver', 'X');
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: 100 });
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [selection, setSelection] = useState<GridRowSelectionModel>({ type: 'include', ids: new Set() });
  const [detailOf, setDetailOf] = useState<string | null>(null);
  const [redeliver, setRedeliver] = useState<AuditRow[] | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const p = await auditApi.query({
        from: fromLocalInput(applied.from), to: fromLocalInput(applied.to), opcode: applied.opcode || undefined,
        transactionId: applied.transactionId || undefined, messageTypes: applied.type ? [applied.type] : undefined,
        failedOnly: applied.failedOnly || undefined, page: page.page, size: page.pageSize,
      });
      setRows(p.items);
      setTotal(p.total);
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

  const search = () => { setPage((p) => ({ ...p, page: 0 })); setApplied({ ...filter }); };

  const columns = useMemo<GridColDef<AuditRow>[]>(() => [
    { field: 'id', headerName: 'ID', width: 90 },
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'messageType', headerName: t('i18n.tc.audit.type', '종류'), width: 110,
      renderCell: ({ row }) => <Chip size="small" variant={isFailed(row) ? 'filled' : 'outlined'} color={isFailed(row) ? 'error' : (TYPE_COLOR[row.messageType] ?? 'default')} label={row.messageType} />,
    },
    { field: 'name', headerName: 'opcode', width: 220 },
    { field: 'transactionId', headerName: t('i18n.tc.audit.transactionId', '트랜잭션 ID'), width: 220 },
    { field: 'route', headerName: t('i18n.tc.audit.route', '보낸 곳 → 받는 곳'), width: 220, sortable: false, valueGetter: (_v, r) => `${r.sender ?? ''} → ${r.receiver ?? ''}` },
    { field: 'principal', headerName: t('i18n.tc.audit.principal', '호출자'), width: 140 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 220 },
  ], [t]);

  const selectedRows = rows.filter((r) => selection.ids.has(r.id));

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
        {canRedeliver && (
          <ScreenAction icon={<ReplayIcon fontSize="small" />} label={t('i18n.tc.redeliver.title', '결과 재전송')}
            onClick={() => setRedeliver(selectedRows)} />
        )}
      </CommandBarActions>
      <FilterBar onSearch={search} onReset={() => { const f = initialFilter(); setFilter(f); setApplied(f); }}>
        <TextField size="small" type="datetime-local" label={t('i18n.tc.audit.from', '부터')} value={filter.from}
          onChange={(e) => setFilter({ ...filter, from: e.target.value })} slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.audit.from', '부터') }, inputLabel: { shrink: true } }} />
        <TextField size="small" type="datetime-local" label={t('i18n.tc.audit.to', '까지')} value={filter.to}
          onChange={(e) => setFilter({ ...filter, to: e.target.value })} slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.audit.to', '까지') }, inputLabel: { shrink: true } }} />
        <TextField slotProps={{ htmlInput: { 'aria-label': "opcode" } }} size="small" label="opcode" value={filter.opcode} onChange={(e) => setFilter({ ...filter, opcode: e.target.value })} />
        <TextField slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.audit.transactionId', '트랜잭션 ID') } }} size="small" label={t('i18n.tc.audit.transactionId', '트랜잭션 ID')} value={filter.transactionId}
          onChange={(e) => setFilter({ ...filter, transactionId: e.target.value })} />
        <TextField select size="small" label={t('i18n.tc.audit.type', '종류')} value={filter.type} sx={{ width: 130 }}
          onChange={(e) => setFilter({ ...filter, type: e.target.value })} slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.audit.type', '종류') }, inputLabel: { shrink: true }, select: { displayEmpty: true } }}>
          <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
          {TYPES.map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}
        </TextField>
        <FormControlLabel control={<Checkbox size="small" checked={filter.failedOnly} onChange={(e) => setFilter({ ...filter, failedOnly: e.target.checked })} />}
          label={t('i18n.tc.audit.failedOnly', '실패만')} />
      </FilterBar>
      {canRedeliver && selectedRows.length > 0 && (
        <Typography variant="caption" color="text.secondary">{t('i18n.tc.audit.selected', '고른 기록')} {selectedRows.length}</Typography>
      )}
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={rows} columns={columns} getRowId={(r) => r.id} loading={loading} density="compact"
            showColumnVerticalBorder showCellVerticalBorder
            pagination paginationMode="server" rowCount={total} paginationModel={page} onPaginationModelChange={setPage}
            pageSizeOptions={[50, 100, 200, 500]}
            checkboxSelection={canRedeliver} rowSelectionModel={selection} onRowSelectionModelChange={setSelection}
            disableRowSelectionOnClick onRowClick={(p) => setDetailOf((p.row as AuditRow).transactionId)}
            getRowClassName={(p) => (p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd')}
            sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }} />
        </Box>
      </Box>
      <AuditDetailDialog transactionId={detailOf} onClose={() => setDetailOf(null)} />
      <RedeliverDialog rows={redeliver} onClose={() => setRedeliver(null)} onDone={() => setSelection({ type: 'include', ids: new Set() })} />
    </PageContainer>
  );
}
