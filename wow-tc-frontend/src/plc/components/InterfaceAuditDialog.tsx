import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { Box, Checkbox, Chip, Dialog, DialogContent, FormControlLabel, MenuItem, Stack, TextField } from '@mui/material';
import { DataGridPro, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid-pro';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showToast, useI18n } from 'wow-mes-frontend-shared';
import { AuditDetailDialog, TYPE_COLOR, isFailed } from '../../ops/components/AuditDetailDialog';
import { auditApi } from '../../ops/services/opsApi';
import type { AuditRow } from '../../ops/types/ops';
import { fmtTime } from '../../shared/format';

const PERIODS: { hours: number; key: string; label: string }[] = [
  { hours: 1, key: 'i18n.tc.audit.last1h', label: '최근 1시간' },
  { hours: 24, key: 'i18n.tc.audit.last24h', label: '최근 24시간' },
  { hours: 24 * 7, key: 'i18n.tc.audit.last7d', label: '최근 7일' },
];

/**
 * 인터페이스 하나의 송수신 이력 — 송수신 기록 화면과 같은 조회(`audit/search/query`)를 opcode 로(서버는 부분 일치).
 * 행을 누르면 그 거래의 기록 전부.
 */
export function InterfaceAuditDialog({ name, onClose }: { name: string | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [hours, setHours] = useState(24);
  const [failedOnly, setFailedOnly] = useState(false);
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: 50 });
  const [detailOf, setDetailOf] = useState<string | null>(null);

  useEffect(() => { setPage((p) => ({ ...p, page: 0 })); }, [name, hours, failedOnly]);
  useEffect(() => {
    if (!name) return;
    let cancelled = false;
    setLoading(true);
    auditApi.query({
      opcode: name, from: new Date(Date.now() - hours * 3600_000).toISOString(), failedOnly: failedOnly || undefined,
      page: page.page, size: page.pageSize,
    })
      .then((p) => { if (!cancelled) { setRows(p.items); setTotal(p.total); } })
      .catch((e) => showToast(extractApiError(e), 'error'))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [name, hours, failedOnly, page]);

  const cols = useMemo<GridColDef<AuditRow>[]>(() => [
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'messageType', headerName: t('i18n.tc.audit.type', '종류'), width: 110,
      renderCell: ({ row }) => <Chip size="small" variant={isFailed(row) ? 'filled' : 'outlined'} color={isFailed(row) ? 'error' : (TYPE_COLOR[row.messageType] ?? 'default')} label={row.messageType} />,
    },
    { field: 'name', headerName: 'opcode', width: 200 },
    { field: 'transactionId', headerName: t('i18n.tc.audit.transactionId', '트랜잭션 ID'), width: 220 },
    { field: 'principal', headerName: t('i18n.tc.audit.principal', '호출자'), width: 130 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 220 },
  ], [t]);

  return (
    <Dialog open={!!name} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.interface.auditTitle', '송수신 이력')} — {name}</DialogTitleClose>
      <DialogContent dividers sx={{ height: '65vh', display: 'flex', flexDirection: 'column', gap: 1 }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <TextField select size="small" label={t('i18n.tc.audit.period', '기간')} value={hours} sx={{ width: 160 }}
            onChange={(e) => setHours(Number(e.target.value))}>
            {PERIODS.map((p) => <MenuItem key={p.hours} value={p.hours}>{t(p.key, p.label)}</MenuItem>)}
          </TextField>
          <FormControlLabel control={<Checkbox size="small" checked={failedOnly} onChange={(e) => setFailedOnly(e.target.checked)} />}
            label={t('i18n.tc.audit.failedOnly', '실패만')} />
        </Stack>
        <Box sx={{ flex: 1, position: 'relative' }}>
          <Box sx={{ position: 'absolute', inset: 0 }}>
            <DataGridPro rows={rows} columns={cols} getRowId={(r) => r.id} loading={loading} density="compact"
              pagination paginationMode="server" rowCount={total} paginationModel={page} onPaginationModelChange={setPage}
              pageSizeOptions={[50, 100, 200]} disableRowSelectionOnClick
              onRowClick={(p) => setDetailOf((p.row as AuditRow).transactionId)} sx={{ '& .MuiDataGrid-row': { cursor: 'pointer' } }} />
          </Box>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose} />
      <AuditDetailDialog transactionId={detailOf} onClose={() => setDetailOf(null)} />
    </Dialog>
  );
}
