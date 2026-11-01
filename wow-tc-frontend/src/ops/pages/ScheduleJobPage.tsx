import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Button, Chip, Paper, Stack, Tab, Tabs, Tooltip, Typography } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import PauseIcon from '@mui/icons-material/Pause';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutlineOutlined';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import EventAvailableIcon from '@mui/icons-material/EventAvailable';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { interfaceApi } from '../../plc/services/plcApi';
import type { InterfaceInfo } from '../../plc/types/plc';
import { JobHistoryPanel } from '../components/JobHistoryPanel';
import { JobPropertiesEditor, propertyErrors, withSpecRows } from '../components/JobPropertiesEditor';
import { ScheduleJobFormDialog, kindLabel } from '../components/ScheduleJobFormDialog';
import { scheduleApi } from '../services/opsApi';
import type { JobDetail, JobKind, ScheduleJob } from '../types/ops';

const SCREEN = 'tc.ops.schedule';

const STATE_COLOR: Record<string, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
  NORMAL: 'success', PAUSED: 'warning', ERROR: 'error', BLOCKED: 'info', COMPLETE: 'default', NONE: 'default',
};

/** 저장할 때 보낼 칸만 — 목록 응답의 최근 실행·다음 실행 등은 빼고 */
function toSave(job: ScheduleJob, jobDetails: JobDetail[]): ScheduleJob {
  const { id, name, schedulerName, use, jobClassName, groupName, triggerType, startDelay, repeatInterval, cronExpression, description } = job;
  return { id, name, schedulerName, use, jobClassName, groupName, triggerType, startDelay, repeatInterval, cronExpression, description, jobDetails };
}

/** 고른 Job 의 속성 — 표에서 고치고 저장(다시 예약) */
function PropertiesTab({ job, kind, interfaces, canEdit, onSave }: {
  job: ScheduleJob;
  kind: JobKind | undefined;
  interfaces: InterfaceInfo[];
  canEdit: boolean;
  onSave: (rows: JobDetail[]) => Promise<string | null>;
}): ReactElement {
  const { t } = useI18n();
  const initial = useMemo(() => withSpecRows(job.jobDetails ?? [], kind), [job, kind]);
  const [rows, setRows] = useState<JobDetail[]>(initial);
  const [error, setError] = useState<string | null>(null);
  const [tried, setTried] = useState(false);
  useEffect(() => { setRows(initial); setError(null); setTried(false); }, [initial]);
  const errors = useMemo(() => propertyErrors(rows, kind, t), [rows, kind, t]);
  const dirty = JSON.stringify(rows) !== JSON.stringify(initial);

  const save = async () => {
    setTried(true);
    if (Object.keys(errors).length) return;
    setError(await onSave(rows.map((r) => ({ ...r, entryKey: r.entryKey.trim() }))));
  };

  return (
    <Box sx={{ overflow: 'auto', height: '100%' }}>
      {error && <Typography color="error" variant="body2" sx={{ mb: 1 }}>{error}</Typography>}
      <JobPropertiesEditor rows={rows} onChange={setRows} kind={kind} interfaces={interfaces} readOnly={!canEdit} errors={tried ? errors : {}} />
      {canEdit && (
        <Stack direction="row" spacing={1} sx={{ mt: 1, justifyContent: 'flex-end' }}>
          <Button size="small" disabled={!dirty} onClick={() => { setRows(initial); setTried(false); setError(null); }}>{t('i18n.common.reset', '초기화')}</Button>
          <Button size="small" variant="contained" disabled={!dirty} onClick={() => { void save(); }}>{t('i18n.common.save', '저장')}</Button>
        </Stack>
      )}
    </Box>
  );
}

/**
 * 스케줄 Job (docs/schedule-jobs.md) — 위는 목록(종류·주기·상태·최근 실행·최근 실패·다음 실행), 행을 고르면 아래에 속성·실행 이력 탭.
 * 속성은 Job 이 실행될 때 읽는 값이라 런타임에 고친다(저장하면 다시 예약). 실행 이력으로 정상 동작을 확인한다.
 */
export default function ScheduleJobPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<ScheduleJob[]>([]);
  const [kinds, setKinds] = useState<JobKind[]>([]);
  const [interfaces, setInterfaces] = useState<InterfaceInfo[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [tab, setTab] = useState(0);
  const [historyKey, setHistoryKey] = useState(0);
  const [form, setForm] = useState<{ open: boolean; initial?: ScheduleJob; error?: string | null }>({ open: false });
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [jobs, ks, ifs] = await Promise.all([scheduleApi.list(), scheduleApi.kinds(), interfaceApi.list().catch(() => [])]);
      setRows(jobs);
      setKinds(ks);
      setInterfaces(ifs.filter((i) => i.interfaceType === 'READ' || i.interfaceType === 'WRITE').sort((a, b) => a.name.localeCompare(b.name)));
      setHistoryKey((k) => k + 1);
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

  /** 저장 — 사용이면 새 주기·속성으로 (다시) 예약하고, 쓰지 않음으로 바꿨으면 예약을 취소한다(저장만으로는 Quartz 에 반영되지 않는다) */
  const persist = useCallback(async (job: ScheduleJob, before?: ScheduleJob) => {
    if (before) await scheduleApi.update(job); else await scheduleApi.register(job);
    if (job.use) await scheduleApi.control('rescheduleJob', job.name);
    else if (before?.use) await scheduleApi.control('unscheduleJob', job.name);
  }, []);

  const save = async (job: ScheduleJob) => {
    try {
      await persist(toSave(job, job.jobDetails ?? []), form.initial);
      setForm({ open: false });
      setSelected(job.name);
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

  const runNow = useCallback((job: ScheduleJob) => {
    void (async () => {
      const ok = await showConfirm({ message: t('i18n.tc.schedule.confirmRunNow', '{name} 을(를) 지금 한 번 실행할까요? 주기는 그대로입니다.').replace('{name}', job.name) });
      if (!ok) return;
      try {
        await scheduleApi.runNow(job.name);
        showToast(t('i18n.tc.schedule.runNowRequested', '실행을 요청했습니다 — 결과는 실행 이력에서 봅니다.'), 'success');
        setSelected(job.name);
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const remove = useCallback((job: ScheduleJob) => {
    void (async () => {
      const ok = await showConfirm({ message: t('i18n.tc.schedule.confirmDelete', '{name} 을(를) 지울까요? 예약도 지운다.').replace('{name}', job.name), severity: 'error' });
      if (!ok) return;
      try {
        await scheduleApi.remove(job.name);
        if (selected === job.name) setSelected(null);
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t, selected]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, r.description, kindLabel(t, r.kind))), [rows, search, t]);
  const current = rows.find((r) => r.name === selected);

  const columns = useMemo<GridColDef<ScheduleJob>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.point.name', '이름'), width: 200 },
    { field: 'kind', headerName: t('i18n.tc.schedule.kind', '종류'), width: 130, valueGetter: (_v, r) => kindLabel(t, r.kind) || (r.jobClassName ?? '').split('.').pop() },
    {
      field: 'trigger', headerName: t('i18n.tc.schedule.trigger', '주기'), width: 140,
      valueGetter: (_v, r) => (r.triggerType === 'cron' ? r.cronExpression ?? '' : `${(r.repeatInterval ?? 0) / 1000}s`),
    },
    {
      field: 'triggerState', headerName: t('i18n.tc.schedule.state', '상태'), width: 100,
      renderCell: ({ row }) => <Chip size="small" color={STATE_COLOR[row.triggerState ?? 'NONE'] ?? 'default'} label={row.triggerState ?? 'NONE'} />,
    },
    { field: 'lastExecutionTime', headerName: t('i18n.tc.schedule.lastRun', '최근 실행'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'lastResult', headerName: t('i18n.tc.schedule.lastResult', '최근 결과'), width: 100,
      renderCell: ({ row }) => (row.lastResult ? (
        <Tooltip title={row.lastRemark ?? ''}>
          <Chip size="small" color={row.lastResult === 'SUCCESS' ? 'success' : 'error'} label={row.lastResult} />
        </Tooltip>
      ) : null),
    },
    {
      field: 'recentFailCount', headerName: t('i18n.tc.schedule.recentFails', '최근 24시간 실패'), width: 130, type: 'number',
      renderCell: ({ row }) => <Box component="span" sx={{ color: row.recentFailCount ? 'error.main' : 'text.secondary', fontWeight: row.recentFailCount ? 600 : 400 }}>{row.recentFailCount ?? 0}</Box>,
    },
    { field: 'nextFireTime', headerName: t('i18n.tc.schedule.next', '다음 실행'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'use', headerName: t('i18n.tc.interface.use', '사용'), width: 70, type: 'boolean' },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 160 },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 110,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [];
        if (perm('U')) acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />);
        if (perm('X')) {
          acts.push(row.triggerState === 'PAUSED'
            ? <GridActionsCellItem key="resume" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => control('resumeScheduleJob', row)} />
            : <GridActionsCellItem key="pause" icon={<PauseIcon />} label={t('i18n.tc.connector.pause', '멈춤')} onClick={() => control('pauseScheduleJob', row)} />);
          acts.push(<GridActionsCellItem key="runNow" icon={<PlayCircleOutlineIcon />} label={t('i18n.tc.schedule.runNow', '지금 실행')} onClick={() => runNow(row)} showInMenu />);
          acts.push(<GridActionsCellItem key="unsched" icon={<EventBusyIcon />} label={t('i18n.tc.schedule.unschedule', '예약 취소')} onClick={() => control('unscheduleJob', row)} showInMenu />);
          acts.push(<GridActionsCellItem key="resched" icon={<EventAvailableIcon />} label={t('i18n.tc.schedule.reschedule', '다시 예약')} onClick={() => control('rescheduleJob', row)} showInMenu />);
        }
        if (perm('D')) acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />);
        return acts;
      },
    },
  ], [t, perm, control, runNow, remove]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.schedule.search', '이름·설명')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Box sx={{ flex: current ? '1 1 45%' : 1, minHeight: 180, display: 'flex', flexDirection: 'column' }}>
          <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']} isInactive={(r) => !r.use}
            onRowClick={(r) => setSelected(r.name)} selectedId={selected ?? undefined} />
        </Box>
        {current ? (
          <Paper variant="outlined" sx={{ flex: '1 1 55%', minHeight: 260, display: 'flex', flexDirection: 'column' }}>
            <Stack direction="row" sx={{ alignItems: 'center', px: 1.5, borderBottom: 1, borderColor: 'divider' }}>
              <Typography variant="subtitle2" sx={{ mr: 2 }}>{current.name}</Typography>
              <Tabs value={tab} onChange={(_e, v: number) => setTab(v)} sx={{ minHeight: 40, '& .MuiTab-root': { minHeight: 40 } }}>
                <Tab label={`${t('i18n.tc.schedule.properties', '속성')} (${current.jobDetails?.length ?? 0})`} />
                <Tab label={t('i18n.tc.schedule.history', '실행 이력')} />
              </Tabs>
            </Stack>
            <Box sx={{ flex: 1, minHeight: 0, p: 1.5 }}>
              {tab === 0 ? (
                <PropertiesTab job={current} kind={kinds.find((k) => k.kind === current.kind)} interfaces={interfaces} canEdit={perm('U')}
                  onSave={async (details) => {
                    try {
                      await persist(toSave(current, details), current);
                      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
                      await loadRef.current();
                      return null;
                    } catch (e) {
                      return extractApiError(e);
                    }
                  }} />
              ) : (
                <JobHistoryPanel jobName={current.name} reloadKey={historyKey} />
              )}
            </Box>
          </Paper>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ px: 1 }}>{t('i18n.tc.schedule.pickJob', '행을 고르면 속성과 실행 이력을 본다')}</Typography>
        )}
      </Box>
      <ScheduleJobFormDialog open={form.open} initial={form.initial} kinds={kinds} interfaces={interfaces} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(j) => { void save(j); }} />
    </PageContainer>
  );
}
