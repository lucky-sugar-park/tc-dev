import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Button, Chip, Dialog, DialogContent, IconButton, MenuItem, Stack, TextField, Tooltip, Typography } from '@mui/material';
import type { GridColDef, GridPaginationModel } from '@mui/x-data-grid-pro';
import RefreshIcon from '@mui/icons-material/Refresh';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showToast, useI18n } from 'wow-mes-frontend-shared';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime } from '../../shared/format';
import { scheduleApi } from '../services/opsApi';
import type { JobHistory } from '../types/ops';
import { AuditDetailDialog } from './AuditDetailDialog';

/** PLC 요청 Job 의 비고에 든 거래 ID — 송수신 기록으로 이어 본다 */
const trxOf = (remark?: string): string | null => /\(trx ([^)\s]+)\)/.exec(remark ?? '')?.[1] ?? null;

/** datetime-local 값 → ISO(비면 undefined) */
const iso = (local: string): string | undefined => (local ? new Date(local).toISOString() : undefined);

/**
 * 한 Job 의 실행 이력 — 서버 쪽 쪽 넘김(최신순), 결과·기간으로 걸러 보기, 자동 새로 고침. 행을 누르면 비고 전체와,
 * PLC 요청이면 그 거래의 송수신 기록.
 */
export function JobHistoryPanel({ jobName, reloadKey }: { jobName: string; reloadKey?: number }): ReactElement {
  const { t } = useI18n();
  const [rows, setRows] = useState<JobHistory[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [auto, setAuto] = useState(0);
  const [pageModel, setPageModel] = useState<GridPaginationModel>({ page: 0, pageSize: 25 });
  const [open, setOpen] = useState<JobHistory | null>(null);
  const [trx, setTrx] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const p = await scheduleApi.history({ jobName, result: result || undefined, from: iso(from), to: iso(to), page: pageModel.page, size: pageModel.pageSize });
      setRows(p.items);
      setTotal(p.total);
    } catch (e) {
      showToast(extractApiError(e), 'error');
    } finally {
      setLoading(false);
    }
  }, [jobName, result, from, to, pageModel]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { void load(); }, [load, reloadKey]);
  // 조건이 바뀌면 첫 쪽으로(이미 첫 쪽이면 그대로 — 같은 요청을 두 번 보내지 않게)
  useEffect(() => { setPageModel((m) => (m.page === 0 ? m : { ...m, page: 0 })); }, [jobName, result, from, to]);
  useEffect(() => {
    if (!auto) return undefined;
    const h = setInterval(() => { void loadRef.current(); }, auto * 1000);
    return () => clearInterval(h);
  }, [auto]);

  const cols = useMemo<GridColDef<JobHistory>[]>(() => [
    { field: 'executionTime', headerName: t('i18n.tc.schedule.ranAt', '실행 시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'executionResult', headerName: t('i18n.tc.schedule.result', '결과'), width: 100,
      renderCell: ({ row }) => <Chip size="small" color={row.executionResult === 'SUCCESS' ? 'success' : 'error'} label={row.executionResult ?? ''} />,
    },
    { field: 'elapsedMs', headerName: t('i18n.tc.schedule.elapsed', '걸린 시간(ms)'), width: 110, type: 'number' },
    { field: 'instanceId', headerName: t('i18n.tc.schedule.instance', '실행 서버'), width: 170 },
    { field: 'nextFireTime', headerName: t('i18n.tc.schedule.next', '다음 실행'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'remark', headerName: t('i18n.tc.schedule.remark', '비고'), flex: 1, minWidth: 300 },
  ], [t]);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <TextField select size="small" label={t('i18n.tc.schedule.result', '결과')} value={result} onChange={(e) => setResult(e.target.value)} sx={{ width: 130 }}>
          <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
          <MenuItem value="SUCCESS">SUCCESS</MenuItem>
          <MenuItem value="FAIL">FAIL</MenuItem>
        </TextField>
        <TextField size="small" type="datetime-local" label={t('i18n.tc.audit.from', '부터')} value={from} onChange={(e) => setFrom(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }} />
        <TextField size="small" type="datetime-local" label={t('i18n.tc.audit.to', '까지')} value={to} onChange={(e) => setTo(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }} />
        <TextField select size="small" label={t('i18n.tc.schedule.autoRefresh', '자동 새로 고침')} value={auto} onChange={(e) => setAuto(Number(e.target.value))} sx={{ width: 140 }}>
          <MenuItem value={0}>{t('i18n.tc.schedule.autoOff', '끔')}</MenuItem>
          <MenuItem value={5}>5s</MenuItem>
          <MenuItem value={30}>30s</MenuItem>
        </TextField>
        <Tooltip title={t('i18n.tc.common.refresh', '새로 고침')}>
          <IconButton size="small" onClick={() => { void load(); }} aria-label={t('i18n.tc.common.refresh', '새로 고침')}><RefreshIcon fontSize="small" /></IconButton>
        </Tooltip>
      </Stack>
      <StandardGrid rows={rows} columns={cols} loading={loading} getRowId={(r) => String(r.seqNo)} pagination minHeight={160}
        onRowClick={(r) => setOpen(r)}
        gridProps={{
          paginationMode: 'server', rowCount: total, paginationModel: pageModel, onPaginationModelChange: setPageModel,
          getRowClassName: (p) => (p.row.executionResult === 'FAIL' ? 'row--fail' : ''),
          sx: { '& .row--fail': { color: 'error.main' }, '& .MuiDataGrid-row': { cursor: 'pointer' } },
        }} />

      <Dialog open={!!open} onClose={() => setOpen(null)} maxWidth="md" fullWidth PaperComponent={DraggablePaper}>
        <DialogTitleClose onClose={() => setOpen(null)}>{t('i18n.tc.schedule.remark', '비고')} — {fmtTime(open?.executionTime)} {open?.executionResult}</DialogTitleClose>
        <DialogContent dividers>
          <Typography component="pre" variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontFamily: 'monospace', m: 0 }}>{open?.remark}</Typography>
        </DialogContent>
        <DialogFooter onClose={() => setOpen(null)}>
          {trxOf(open?.remark) && (
            <Button onClick={() => setTrx(trxOf(open?.remark))}>{t('i18n.tc.schedule.openAudit', '송수신 기록 보기')}</Button>
          )}
        </DialogFooter>
      </Dialog>
      <AuditDetailDialog transactionId={trx} onClose={() => setTrx(null)} />
    </Box>
  );
}
