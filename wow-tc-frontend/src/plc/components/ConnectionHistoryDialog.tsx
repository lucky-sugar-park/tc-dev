import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { Box, Dialog, DialogContent } from '@mui/material';
import { DataGridPro, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid-pro';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showToast, useI18n } from 'wow-mes-frontend-shared';
import { connectorApi } from '../services/plcApi';
import { fmtTime } from '../../shared/format';
import type { ConnectionHistory } from '../types/plc';

/** 커넥터 연결 이력(DB) — 연결·끊김·게시·해제 이벤트, 최근 것부터 쪽 단위 */
export function ConnectionHistoryDialog({ name, onClose }: { name: string | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [rows, setRows] = useState<ConnectionHistory[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: 50 });

  useEffect(() => { setPage((p) => ({ ...p, page: 0 })); }, [name]);
  useEffect(() => {
    if (!name) return;
    let cancelled = false;
    setLoading(true);
    connectorApi.history(name, page.page, page.pageSize)
      .then((r) => { if (!cancelled) { setRows(r.rows ?? []); setTotal(r.totalRowsCount ?? 0); } })
      .catch((e) => showToast(extractApiError(e), 'error'))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [name, page]);

  const cols = useMemo<GridColDef<ConnectionHistory>[]>(() => [
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'eventType', headerName: t('i18n.tc.history.event', '이벤트'), width: 110 },
    { field: 'cause', headerName: t('i18n.tc.history.cause', '원인'), width: 160 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 260 },
  ], [t]);

  return (
    <Dialog open={!!name} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.history.title', '연결 이력')} — {name}</DialogTitleClose>
      <DialogContent dividers sx={{ height: '60vh', display: 'flex' }}>
        <Box sx={{ flex: 1, position: 'relative' }}>
          <Box sx={{ position: 'absolute', inset: 0 }}>
            <DataGridPro rows={rows} columns={cols} getRowId={(r) => r.id} loading={loading} density="compact"
              pagination paginationMode="server" rowCount={total} paginationModel={page} onPaginationModelChange={setPage}
              pageSizeOptions={[50, 100, 200]} disableRowSelectionOnClick />
          </Box>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
