import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Chip, FormControlLabel, IconButton, InputAdornment, MenuItem, Switch, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import { type GridColDef } from '@mui/x-data-grid-pro';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { CommandBarActions, FilterBar, PageContainer, ScreenAction, useI18n } from 'wow-mes-frontend-shared';
import { RangeExportDialog, RangeGrid, useRangeGrid, type RangeParams } from '../../shared/components/RangeGrid';
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

/**
 * 스케줄 Job 실행 이력 (docs/schedule-jobs.md §5) — 모든 Job 의 실행 이력을 Job 이름·설명, Job 상태, 실행 기간, 실행 결과로 찾는다.
 * 구간 스크롤 표(docs/range-grid-design.md): 최신순, 스크롤하면 그 구간을 받고, 10만 건 넘으면 구간 이동. "최근 24시간" 을 켜면 기간 대신
 * 지금부터 24시간 전까지. 행을 누르면 비고 전체와 송수신 기록.
 */
export default function ScheduleHistoryPage(): ReactElement {
  const { t } = useI18n();
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const appliedRef = useRef(applied);
  appliedRef.current = applied;
  const [open, setOpen] = useState<JobHistory | null>(null);
  const [exporting, setExporting] = useState(false);

  const fetchRange = useCallback((p: RangeParams) => {
    const f = appliedRef.current;
    return scheduleApi.historyRange({
      jobSearch: f.search.trim() || undefined, jobState: f.state || undefined,
      from: f.recent24h ? new Date(Date.now() - 24 * 3600_000).toISOString() : fromLocalInput(f.from),
      to: f.recent24h ? undefined : fromLocalInput(f.to), result: f.result || undefined,
    }, p);
  }, []);
  const grid = useRangeGrid<JobHistory>(fetchRange);

  const firstRef = useRef(true);
  useEffect(() => {
    if (firstRef.current) { firstRef.current = false; return; }
    grid.refresh();
  }, [applied]); // eslint-disable-line react-hooks/exhaustive-deps

  const search = () => setApplied({ ...filter });
  const reset = () => { const f = initialFilter(); setFilter(f); setApplied(f); };

  const columns = useMemo<GridColDef<JobHistory>[]>(() => [
    { field: 'jobName', headerName: t('i18n.tc.schedule.job', '스케줄 Job'), width: 220 },
    {
      field: 'executionResult', headerName: t('i18n.tc.schedule.runResult', '실행 결과'), width: 100,
      renderCell: ({ row }) => (row.executionResult
        ? <Chip size="small" color={row.executionResult === 'SUCCESS' ? 'success' : 'error'} label={row.executionResult} /> : null),
    },
    { field: 'elapsedMs', headerName: t('i18n.tc.schedule.elapsed', '걸린 시간(ms)'), width: 110, type: 'number' },
    { field: 'instanceId', headerName: t('i18n.tc.schedule.instance', '실행 서버'), width: 180 },
    { field: 'executionTime', headerName: t('i18n.tc.schedule.runTime', '실행 시간'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'nextFireTime', headerName: t('i18n.tc.schedule.next', '다음 실행'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'remark', headerName: t('i18n.tc.schedule.remark', '비고'), flex: 1, minWidth: 300 },
  ], [t]);

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => grid.refresh()} />
        <ScreenAction kind="excelDown" onClick={() => setExporting(true)} disabled={!grid.total} />
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
      <RangeGrid grid={grid} title={t('i18n.tc.schedule.histListTitle', '스케줄 Job 실행 이력')} columns={columns} getRowId={(r) => r.seqNo}
        gridProps={{
          onRowClick: (p) => { if (p.row.jobName) setOpen(p.row); },
          getRowClassName: (p) => `${p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd'}${p.row.executionResult === 'FAIL' ? ' row--fail' : ''}`,
          sx: { '& .MuiDataGrid-row': { cursor: 'pointer' }, '& .row--fail': { color: 'error.main' } },
        }} />
      <JobRemarkDialog history={open} onClose={() => setOpen(null)} />
      <RangeExportDialog open={exporting} onClose={() => setExporting(false)} grid={grid}
        fileName={t('i18n.tc.schedule.histExcelFile', '스케줄Job실행이력')} sheetName={t('i18n.tc.schedule.histListTitle', '스케줄 Job 실행 이력')}
        toRow={(r, no) => ({
          [t('i18n.tc.schedule.no', '번호')]: no, [t('i18n.tc.schedule.job', '스케줄 Job')]: r.jobName,
          [t('i18n.tc.schedule.runResult', '실행 결과')]: r.executionResult ?? '', [t('i18n.tc.schedule.elapsed', '걸린 시간(ms)')]: r.elapsedMs ?? '',
          [t('i18n.tc.schedule.instance', '실행 서버')]: r.instanceId ?? '', [t('i18n.tc.schedule.runTime', '실행 시간')]: fmtTime(r.executionTime),
          [t('i18n.tc.schedule.next', '다음 실행')]: fmtTime(r.nextFireTime), [t('i18n.tc.schedule.remark', '비고')]: r.remark ?? '',
        })} />
    </PageContainer>
  );
}
