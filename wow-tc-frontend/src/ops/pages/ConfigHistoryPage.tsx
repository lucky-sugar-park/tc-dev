import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Dialog, DialogContent, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { type GridColDef } from '@mui/x-data-grid-pro';
import {
  CommandBarActions, DialogFooter, DialogTitleClose, DraggablePaper, FilterBar, PageContainer, ScreenAction, useI18n,
} from 'wow-mes-frontend-shared';
import { RangeExportDialog, RangeGrid, useRangeGrid, type RangeParams } from '../../shared/components/RangeGrid';
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

const changedOf = (r: ConfigChange) => (r.action === 'UPDATE' ? changedKeys(r.beforeJson, r.afterJson).join(', ') : r.detail ?? '');

const toLocalInput = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);

type Filter = { from: string; to: string; targetType: string; targetId: string; actorId: string };

const initialFilter = (): Filter => ({ from: toLocalInput(new Date(Date.now() - 7 * 86400_000)), to: '', targetType: '', targetId: '', actorId: '' });

/**
 * 설정 변경 이력 (docs/frontend-design.md §5) — 누가 언제 무엇을 바꿨는지(커넥터·인터페이스·전달·스케줄·사용자·API 키 …). 구간 스크롤 표
 * (docs/range-grid-design.md): 최신순, 스크롤하면 그 구간을 받고, 10만 건 넘으면 구간 이동. 행을 누르면 앞뒤 값과 바뀐 칸.
 */
export default function ConfigHistoryPage(): ReactElement {
  const { t } = useI18n();
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const appliedRef = useRef(applied);
  appliedRef.current = applied;
  const [detail, setDetail] = useState<ConfigChange | null>(null);
  const [exporting, setExporting] = useState(false);

  const fetchRange = useCallback((p: RangeParams) => {
    const f = appliedRef.current;
    return configHistoryApi.range({
      from: f.from ? new Date(f.from).toISOString() : undefined, to: f.to ? new Date(f.to).toISOString() : undefined,
      targetType: f.targetType || undefined, targetId: f.targetId || undefined, actorId: f.actorId || undefined,
    }, p);
  }, []);
  const grid = useRangeGrid<ConfigChange>(fetchRange);

  const firstRef = useRef(true);
  useEffect(() => {
    if (firstRef.current) { firstRef.current = false; return; }
    grid.refresh();
  }, [applied]); // eslint-disable-line react-hooks/exhaustive-deps

  const columns = useMemo<GridColDef<ConfigChange>[]>(() => [
    { field: 'changedAt', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'action', headerName: t('i18n.tc.confighist.action', '동작'), width: 100,
      renderCell: ({ row }) => (row.action ? <Chip size="small" variant="outlined" color={ACTION_COLOR[row.action] ?? 'default'} label={row.action} /> : null),
    },
    { field: 'targetType', headerName: t('i18n.tc.confighist.targetType', '대상 종류'), width: 140 },
    { field: 'targetName', headerName: t('i18n.tc.confighist.target', '대상'), width: 220, valueGetter: (_v, r) => r.targetName ?? r.targetId },
    { field: 'actorId', headerName: t('i18n.tc.confighist.actor', '바꾼 사람'), width: 140, valueGetter: (_v, r) => (r.actorId ? `${r.actorId} (${r.actorType})` : '') },
    { field: 'actorIp', headerName: 'IP', width: 120 },
    { field: 'changed', headerName: t('i18n.tc.confighist.changed', '바뀐 칸'), flex: 1, minWidth: 200, valueGetter: (_v, r) => (r.id ? changedOf(r) : '') },
  ], [t]);

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => grid.refresh()} />
        <ScreenAction kind="excelDown" onClick={() => setExporting(true)} disabled={!grid.total} />
      </CommandBarActions>
      <FilterBar onSearch={() => setApplied({ ...filter })} onReset={() => { const f = initialFilter(); setFilter(f); setApplied(f); }}>
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
      <RangeGrid grid={grid} title={t('i18n.tc.confighist.listTitle', '설정 변경 이력')} columns={columns} getRowId={(r) => r.id}
        gridProps={{
          onRowClick: (p) => { if (p.row.id) setDetail(p.row); },
          sx: { '& .MuiDataGrid-row': { cursor: 'pointer' } },
        }} />
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
      <RangeExportDialog open={exporting} onClose={() => setExporting(false)} grid={grid}
        fileName={t('i18n.tc.confighist.excelFile', '설정변경이력')} sheetName={t('i18n.tc.confighist.listTitle', '설정 변경 이력')}
        toRow={(r, no) => ({
          [t('i18n.tc.schedule.no', '번호')]: no, [t('i18n.tc.audit.time', '시각')]: fmtTime(r.changedAt),
          [t('i18n.tc.confighist.action', '동작')]: r.action, [t('i18n.tc.confighist.targetType', '대상 종류')]: r.targetType,
          [t('i18n.tc.confighist.targetId', '대상 ID')]: r.targetId, [t('i18n.tc.confighist.target', '대상')]: r.targetName ?? '',
          [t('i18n.tc.confighist.actor', '바꾼 사람')]: r.actorId, [t('i18n.tc.confighist.actorType', '행위자 종류')]: r.actorType, IP: r.actorIp ?? '',
          [t('i18n.tc.confighist.changed', '바뀐 칸')]: changedOf(r),
        })} />
    </PageContainer>
  );
}
