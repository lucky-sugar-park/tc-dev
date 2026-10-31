import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Dialog, DialogContent } from '@mui/material';
import { DataGridPro, GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import PauseIcon from '@mui/icons-material/Pause';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import EventAvailableIcon from '@mui/icons-material/EventAvailable';
import HistoryIcon from '@mui/icons-material/History';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  DialogFooter, DialogTitleClose, DraggablePaper, LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { interfaceApi } from '../../plc/services/plcApi';
import { JOB_KINDS, ScheduleJobFormDialog, kindOf } from '../components/ScheduleJobFormDialog';
import { scheduleApi } from '../services/opsApi';
import type { JobHistory, ScheduleJob } from '../types/ops';

const SCREEN = 'tc.ops.schedule';

const STATE_COLOR: Record<string, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
  NORMAL: 'success', PAUSED: 'warning', ERROR: 'error', BLOCKED: 'info', COMPLETE: 'default', NONE: 'default',
};

function HistoryDialog({ name, onClose }: { name: string | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [rows, setRows] = useState<JobHistory[]>([]);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    setRows([]);
    if (!name) return;
    setLoading(true);
    scheduleApi.history(name, 0, 200).then(setRows).catch((e) => showToast(extractApiError(e), 'error')).finally(() => setLoading(false));
  }, [name]);
  const cols = useMemo<GridColDef<JobHistory>[]>(() => [
    { field: 'executionTime', headerName: t('i18n.tc.schedule.ranAt', '실행 시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'executionResult', headerName: t('i18n.tc.schedule.result', '결과'), width: 110,
      renderCell: ({ row }) => <Chip size="small" color={row.executionResult === 'SUCCESS' ? 'success' : 'error'} label={row.executionResult ?? ''} />,
    },
    { field: 'nextFireTime', headerName: t('i18n.tc.schedule.next', '다음 실행'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'remark', headerName: t('i18n.tc.schedule.remark', '비고'), flex: 1, minWidth: 300 },
  ], [t]);
  return (
    <Dialog open={!!name} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.schedule.history', '실행 이력')} — {name}</DialogTitleClose>
      <DialogContent dividers sx={{ height: '60vh', display: 'flex' }}>
        <Box sx={{ flex: 1, position: 'relative' }}>
          <Box sx={{ position: 'absolute', inset: 0 }}>
            <DataGridPro rows={rows} columns={cols} getRowId={(r) => r.seqNo} loading={loading} density="compact" disableRowSelectionOnClick hideFooter />
          </Box>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}

/**
 * 스케줄 Job (docs/frontend-design.md §5) — 목록(종류·주기·상태), 등록·수정(종류별 값), 멈춤·재개·예약 취소·다시 예약, 실행 이력, 삭제.
 * 옛 화면의 클래스 이름 입력 대신 종류를 고른다.
 */
export default function ScheduleJobPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<ScheduleJob[]>([]);
  const [interfaces, setInterfaces] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: ScheduleJob; error?: string | null }>({ open: false });
  const [historyOf, setHistoryOf] = useState<string | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [jobs, ifs] = await Promise.all([scheduleApi.list(), interfaceApi.list().catch(() => [])]);
      setRows(jobs);
      setInterfaces(ifs.filter((i) => i.interfaceType === 'READ' || i.interfaceType === 'WRITE').map((i) => i.name).sort());
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { if (active) void loadRef.current(); }, [active]);

  const openEdit = useCallback(async (job: ScheduleJob) => {
    try {
      setForm({ open: true, initial: { ...job, jobDetails: await scheduleApi.details(job.name) } });
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, []);

  const save = async (job: ScheduleJob) => {
    try {
      if (form.initial) await scheduleApi.update(job); else await scheduleApi.register(job);
      // 저장만으로는 예약되지 않는다 — 사용이면 새 주기·값으로 (다시) 예약하고, 쓰지 않음으로 바꿨으면 예약을 취소한다
      if (job.use) await scheduleApi.control('rescheduleJob', job.name);
      else if (form.initial?.use) await scheduleApi.control('unscheduleJob', job.name);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const control = useCallback((op: 'pauseScheduleJob' | 'resumeScheduleJob' | 'unscheduleJob' | 'rescheduleJob', job: ScheduleJob) => {
    void (async () => {
      try {
        await scheduleApi.control(op, job.name);
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, []);

  const remove = useCallback((job: ScheduleJob) => {
    void (async () => {
      const ok = await showConfirm({ message: t('i18n.tc.schedule.confirmDelete', '{name} 을(를) 지울까요? 예약도 지운다.').replace('{name}', job.name), severity: 'error' });
      if (!ok) return;
      try {
        await scheduleApi.remove(job.name);
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, r.description, r.jobClassName)), [rows, search]);

  const columns = useMemo<GridColDef<ScheduleJob>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.point.name', '이름'), width: 200 },
    {
      field: 'kind', headerName: t('i18n.tc.schedule.kind', '종류'), width: 150,
      valueGetter: (_v, r) => { const k = JOB_KINDS.find((x) => x.kind === kindOf(r))!; return k.kind === 'OTHER' ? (r.jobClassName ?? '').split('.').pop() : t(k.labelCode, k.label); },
    },
    {
      field: 'trigger', headerName: t('i18n.tc.schedule.trigger', '주기'), width: 170,
      valueGetter: (_v, r) => (r.triggerType === 'cron' ? r.cronExpression ?? '' : `${(r.repeatInterval ?? 0) / 1000}s`),
    },
    {
      field: 'triggerState', headerName: t('i18n.tc.schedule.state', '상태'), width: 110,
      renderCell: ({ row }) => <Chip size="small" color={STATE_COLOR[row.triggerState ?? 'NONE'] ?? 'default'} label={row.triggerState ?? 'NONE'} />,
    },
    { field: 'stateChangedTime', headerName: t('i18n.tc.schedule.stateAt', '상태 바뀐 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'use', headerName: t('i18n.tc.interface.use', '사용'), width: 70, type: 'boolean' },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 160 },
    {
      field: 'actions', type: 'actions', headerName: '', width: 110,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [];
        if (perm('U')) acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => { void openEdit(row); }} />);
        if (perm('X')) {
          acts.push(row.triggerState === 'PAUSED'
            ? <GridActionsCellItem key="resume" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => control('resumeScheduleJob', row)} />
            : <GridActionsCellItem key="pause" icon={<PauseIcon />} label={t('i18n.tc.connector.pause', '멈춤')} onClick={() => control('pauseScheduleJob', row)} />);
          acts.push(<GridActionsCellItem key="unsched" icon={<EventBusyIcon />} label={t('i18n.tc.schedule.unschedule', '예약 취소')} onClick={() => control('unscheduleJob', row)} showInMenu />);
          acts.push(<GridActionsCellItem key="resched" icon={<EventAvailableIcon />} label={t('i18n.tc.schedule.reschedule', '다시 예약')} onClick={() => control('rescheduleJob', row)} showInMenu />);
        }
        acts.push(<GridActionsCellItem key="hist" icon={<HistoryIcon />} label={t('i18n.tc.schedule.history', '실행 이력')} onClick={() => setHistoryOf(row.name)} showInMenu />);
        if (perm('D')) acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />);
        return acts;
      },
    },
  ], [t, perm, openEdit, control, remove]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.schedule.search', '이름·설명')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']} isInactive={(r) => !r.use} />
      </Box>
      <ScheduleJobFormDialog open={form.open} initial={form.initial} interfaces={interfaces} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(j) => { void save(j); }} />
      <HistoryDialog name={historyOf} onClose={() => setHistoryOf(null)} />
    </PageContainer>
  );
}
