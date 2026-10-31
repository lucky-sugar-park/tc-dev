import { useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, FormControlLabel, MenuItem, Switch, TextField, Typography } from '@mui/material';
import { DataGridPro, type GridColDef } from '@mui/x-data-grid-pro';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import { CommandBarActions, FilterBar, PageContainer, ScreenAction, serverEvents, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime, matches } from '../../shared/format';
import { AuditDetailDialog, TYPE_COLOR, isFailed } from '../components/AuditDetailDialog';
import type { AuditRow } from '../types/ops';

const MAX_OPTIONS = [100, 500, 1000];

/**
 * 메시지 모니터 (docs/frontend-design.md §5) — 들어오는 요청·응답·오류·전달을 SSE(audit_message)로 바로 본다. 멈추면 받은 것을 쌓지 않는다.
 * 옛 화면은 SSE 주소 버그로 아무것도 받지 못했다. 이벤트는 기록을 남긴 인스턴스에서만 오므로 다중화면 이 화면이 붙은 인스턴스의 것만 보인다.
 */
export default function MessageMonitorPage(): ReactElement {
  const { t } = useI18n();
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [paused, setPaused] = useState(false);
  const [max, setMax] = useState(500);
  const [q, setQ] = useState('');
  const [onlyFailed, setOnlyFailed] = useState(false);
  const [detailOf, setDetailOf] = useState<string | null>(null);
  const [connected, setConnected] = useState<boolean | null>(null);
  const pausedRef = useRef(paused);
  pausedRef.current = paused;
  const maxRef = useRef(max);
  maxRef.current = max;

  // 화면이 떠 있는 동안(탭이 숨어도) 받는다 — 멈춤이면 버린다
  useEffect(() => serverEvents.subscribe('audit_message', (data: unknown) => {
    if (pausedRef.current) return;
    const row = (typeof data === 'string' ? JSON.parse(data) : data) as AuditRow;
    setRows((rs) => [row, ...rs].slice(0, maxRef.current));
  }), []);
  useEffect(() => serverEvents.onStatus((c: boolean) => setConnected(c)), []);

  const shown = useMemo(() => rows.filter((r) => (!onlyFailed || isFailed(r)) && matches(q, r.name, r.transactionId, r.description)), [rows, onlyFailed, q]);
  const stats = useMemo(() => ({
    total: rows.length, failed: rows.filter(isFailed).length,
  }), [rows]);

  const columns = useMemo<GridColDef<AuditRow>[]>(() => [
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'messageType', headerName: t('i18n.tc.audit.type', '종류'), width: 110,
      renderCell: ({ row }) => <Chip size="small" variant={isFailed(row) ? 'filled' : 'outlined'} color={isFailed(row) ? 'error' : (TYPE_COLOR[row.messageType] ?? 'default')} label={row.messageType} />,
    },
    { field: 'name', headerName: 'opcode', width: 220 },
    { field: 'transactionId', headerName: t('i18n.tc.audit.transactionId', '트랜잭션 ID'), width: 220 },
    { field: 'principal', headerName: t('i18n.tc.audit.principal', '호출자'), width: 130 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 220 },
  ], [t]);

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction icon={<DeleteSweepIcon fontSize="small" />} label={t('i18n.tc.monitor.clear', '비우기')} onClick={() => setRows([])} />
      </CommandBarActions>
      <FilterBar>
        <TextField slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.monitor.filter', 'opcode·거래 ID·설명') } }} size="small" label={t('i18n.tc.monitor.filter', 'opcode·거래 ID·설명')} value={q} onChange={(e) => setQ(e.target.value)} />
        <FormControlLabel control={<Switch checked={onlyFailed} onChange={(e) => setOnlyFailed(e.target.checked)} />} label={t('i18n.tc.audit.failedOnly', '실패만')} />
        <FormControlLabel control={<Switch checked={paused} onChange={(e) => setPaused(e.target.checked)} />} label={t('i18n.tc.monitor.pause', '멈춤')} />
        <TextField slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.monitor.max', '최대 행') } }} select size="small" label={t('i18n.tc.monitor.max', '최대 행')} value={max} sx={{ width: 110 }} onChange={(e) => setMax(Number(e.target.value))}>
          {MAX_OPTIONS.map((n) => <MenuItem key={n} value={n}>{n}</MenuItem>)}
        </TextField>
      </FilterBar>
      <Typography variant="caption" color="text.secondary">
        {connected === false ? t('i18n.tc.monitor.disconnected', '실시간 연결 끊김 — 다시 잇는 중') : t('i18n.tc.monitor.live', '실시간')}
        {' · '}{t('i18n.tc.monitor.received', '받은 것 {n} · 실패 {f}').replace('{n}', String(stats.total)).replace('{f}', String(stats.failed))}
      </Typography>
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={shown} columns={columns} getRowId={(r) => `${r.id}`} density="compact" hideFooter
            disableRowSelectionOnClick onRowClick={(p) => setDetailOf((p.row as AuditRow).transactionId)}
            getRowClassName={(p) => (p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd')}
            sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }} />
        </Box>
      </Box>
      <AuditDetailDialog transactionId={detailOf} onClose={() => setDetailOf(null)} />
    </PageContainer>
  );
}
