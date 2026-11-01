import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, FormControlLabel, IconButton, InputAdornment, MenuItem, Switch, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import { DataGridPro, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid-pro';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { CommandBarActions, FilterBar, LoadErrorState, PageContainer, ScreenAction, extractApiError, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { JobRemarkDialog } from '../components/JobRemarkDialog';
import { scheduleApi } from '../services/opsApi';
import type { JobHistory } from '../types/ops';

/** Job 상태(Quartz 트리거 상태) */
const STATES = ['NORMAL', 'PAUSED', 'ERROR', 'BLOCKED', 'COMPLETE', 'NONE'];

/** datetime-local 값(이 브라우저 시각) → ISO */
const fromLocalInput = (s: string) => (s ? new Date(s).toISOString() : undefined);

type Filter = { search: string; state: string; from: string; to: string; result: '' | 'SUCCESS' | 'FAIL'; recent24h: boolean };

const initialFilter = (): Filter => ({ search: '', state: '', from: '', to: '', result: '', recent24h: false });

type Row = JobHistory & { no: number };

/**
 * 스케줄 Job 실행 이력 (docs/schedule-jobs.md §5) — 모든 Job 의 실행 이력을 Job 이름·설명, Job 상태, 실행 기간, 실행 결과로 찾는다
 * (서버 쪽 쪽 넘김, 최신순). "최근 24시간" 을 켜면 기간 대신 지금부터 24시간 전까지. 행을 누르면 비고 전체와 송수신 기록.
 */
export default function ScheduleHistoryPage(): ReactElement {
  const { t } = useI18n();
  const active = useIsActiveScreen();
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: 100 });
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState<JobHistory | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const p = await scheduleApi.history({
        jobSearch: applied.search.trim() || undefined, jobState: applied.state || undefined,
        from: applied.recent24h ? new Date(Date.now() - 24 * 3600_000).toISOString() : fromLocalInput(applied.from),
        to: applied.recent24h ? undefined : fromLocalInput(applied.to),
        result: applied.result || undefined, page: page.page, size: page.pageSize,
      });
      setRows(p.items.map((r, i) => ({ ...r, no: page.page * page.pageSize + i + 1 })));
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
  const reset = () => { const f = initialFilter(); setFilter(f); setPage((p) => ({ ...p, page: 0 })); setApplied(f); };

  const columns = useMemo<GridColDef<Row>[]>(() => [
    { field: 'no', headerName: t('i18n.tc.schedule.no', '번호'), width: 80, type: 'number', sortable: false },
    { field: 'jobName', headerName: t('i18n.tc.schedule.job', '스케줄 Job'), width: 220, sortable: false },
    {
      field: 'executionResult', headerName: t('i18n.tc.schedule.runResult', '실행 결과'), width: 100, sortable: false,
      renderCell: ({ row }) => <Chip size="small" color={row.executionResult === 'SUCCESS' ? 'success' : 'error'} label={row.executionResult ?? ''} />,
    },
    { field: 'elapsedMs', headerName: t('i18n.tc.schedule.elapsed', '걸린 시간(ms)'), width: 110, type: 'number', sortable: false },
    { field: 'instanceId', headerName: t('i18n.tc.schedule.instance', '실행 서버'), width: 180, sortable: false },
    { field: 'executionTime', headerName: t('i18n.tc.schedule.runTime', '실행 시간'), width: 170, sortable: false, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'nextFireTime', headerName: t('i18n.tc.schedule.next', '다음 실행'), width: 170, sortable: false, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'remark', headerName: t('i18n.tc.schedule.remark', '비고'), flex: 1, minWidth: 300, sortable: false },
  ], [t]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
      </CommandBarActions>
      <FilterBar onSearch={search} onReset={reset}>
        <TextField size="small" placeholder={t('i18n.tc.schedule.search', '이름·설명')} value={filter.search}
          onChange={(e) => setFilter({ ...filter, search: e.target.value })}
          slotProps={{
            htmlInput: { 'aria-label': t('i18n.common.unifiedSearch', '통합검색') },
            input: {
              startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" color="action" /></InputAdornment>,
              endAdornment: filter.search ? (
                <InputAdornment position="end">
                  <IconButton size="small" aria-label={t('i18n.common.clearSearch', '검색어 지우기')} onClick={() => setFilter({ ...filter, search: '' })}><ClearIcon fontSize="small" /></IconButton>
                </InputAdornment>
              ) : undefined,
            },
          }}
          sx={{ width: 240, '& .MuiInputBase-root': { fontSize: '0.8rem', height: 32 } }} />
        <TextField select size="small" label={t('i18n.tc.schedule.jobState', 'Job 상태')} value={filter.state} sx={{ width: 130 }}
          onChange={(e) => setFilter({ ...filter, state: e.target.value })}
          slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.schedule.jobState', 'Job 상태') }, inputLabel: { shrink: true }, select: { displayEmpty: true } }}>
          <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
          {STATES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
        </TextField>
        <TextField size="small" type="datetime-local" label={t('i18n.tc.schedule.runFrom', '실행 시작')} value={filter.from} disabled={filter.recent24h}
          onChange={(e) => setFilter({ ...filter, from: e.target.value })}
          slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.schedule.runFrom', '실행 시작') }, inputLabel: { shrink: true } }} />
        <TextField size="small" type="datetime-local" label={t('i18n.tc.schedule.runTo', '실행 끝')} value={filter.to} disabled={filter.recent24h}
          onChange={(e) => setFilter({ ...filter, to: e.target.value })}
          slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.schedule.runTo', '실행 끝') }, inputLabel: { shrink: true } }} />
        <ToggleButtonGroup size="small" exclusive aria-label={t('i18n.tc.schedule.runResult', '실행 결과')} value={filter.result}
          onChange={(_e, v: Filter['result'] | null) => { if (v !== null) setFilter({ ...filter, result: v }); }}>
          <ToggleButton value="">{t('i18n.tc.common.all', '전체')}</ToggleButton>
          <ToggleButton value="SUCCESS">{t('i18n.tc.schedule.success', '성공')}</ToggleButton>
          <ToggleButton value="FAIL">{t('i18n.tc.schedule.fail', '실패')}</ToggleButton>
        </ToggleButtonGroup>
        <FormControlLabel control={<Switch size="small" checked={filter.recent24h} onChange={(e) => setFilter({ ...filter, recent24h: e.target.checked })} />}
          label={t('i18n.tc.schedule.recent24h', '최근 24시간')} />
      </FilterBar>
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={rows} columns={columns} getRowId={(r) => r.seqNo} loading={loading} density="compact"
            showColumnVerticalBorder showCellVerticalBorder
            pagination paginationMode="server" rowCount={total} paginationModel={page} onPaginationModelChange={setPage}
            pageSizeOptions={[50, 100, 200, 500]}
            disableRowSelectionOnClick onRowClick={(p) => setOpen(p.row as Row)}
            getRowClassName={(p) => `${p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd'}${p.row.executionResult === 'FAIL' ? ' row--fail' : ''}`}
            sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' }, '& .row--fail': { color: 'error.main' } }} />
        </Box>
      </Box>
      <JobRemarkDialog history={open} onClose={() => setOpen(null)} />
    </PageContainer>
  );
}
