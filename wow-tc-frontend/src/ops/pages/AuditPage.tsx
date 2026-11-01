import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Checkbox, Chip, FormControlLabel, MenuItem, TextField, Typography } from '@mui/material';
import { GridActionsCellItem, type GridColDef, type GridRowSelectionModel } from '@mui/x-data-grid-pro';
import SendOutlinedIcon from '@mui/icons-material/SendOutlined';
import { CommandBarActions, FilterBar, PageContainer, ScreenAction, useI18n } from 'wow-mes-frontend-shared';
import { RangeExportDialog, RangeGrid, useRangeGrid, type RangeParams } from '../../shared/components/RangeGrid';
import { fmtTime } from '../../shared/format';
import { useCan } from '../../shared/usePerm';
import { AuditDetailDialog, TYPE_COLOR, isFailed } from '../components/AuditDetailDialog';
import { RedeliverDialog } from '../components/RedeliverDialog';
import { auditApi } from '../services/opsApi';
import type { AuditRow } from '../types/ops';

const TYPES = ['REQUEST', 'RESPONSE', 'ERROR', 'DELIVERY'];

/** 결과 재전송할 수 있는 기록 — 전달 기록은 같은 대상으로, 응답·오류 기록은 고른 대상으로(docs/redelivery-design.md) */
const REDELIVERABLE = ['DELIVERY', 'RESPONSE', 'ERROR'];

/** datetime-local 값(이 브라우저 시각) ↔ ISO */
const toLocalInput = (d: Date) => new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
const fromLocalInput = (s: string) => (s ? new Date(s).toISOString() : undefined);

type Filter = { from: string; to: string; opcode: string; transactionId: string; type: string; failedOnly: boolean };

const initialFilter = (): Filter => ({
  from: toLocalInput(new Date(Date.now() - 3600_000)), to: '', opcode: '', transactionId: '', type: '', failedOnly: false,
});

/**
 * 송수신 기록 (docs/frontend-design.md §5·§14) — 기간·opcode·거래 ID·종류·실패만으로 찾는다. 구간 스크롤 표(docs/range-grid-design.md):
 * 최신순, 스크롤하면 그 구간을 받고, 10만 건 넘으면 구간 이동. 목록에는 본문이 없다 — 행을 누르면 그 거래의 기록 전부(요청·응답·오류·전달)를
 * 본문과 함께 본다. 관리자는 골라서 결과 재전송.
 */
export default function AuditPage(): ReactElement {
  const { t } = useI18n();
  const canRedeliver = useCan('action.tc.redeliver', 'X');
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const appliedRef = useRef(applied);
  appliedRef.current = applied;
  const [selection, setSelection] = useState<GridRowSelectionModel>({ type: 'include', ids: new Set() });
  const [detailOf, setDetailOf] = useState<string | null>(null);
  const [redeliver, setRedeliver] = useState<AuditRow[] | null>(null);
  const [exporting, setExporting] = useState(false);

  const fetchRange = useCallback((p: RangeParams, extra?: Record<string, boolean>) => {
    const f = appliedRef.current;
    return auditApi.range({
      from: fromLocalInput(f.from), to: fromLocalInput(f.to), opcode: f.opcode || undefined, transactionId: f.transactionId || undefined,
      messageTypes: f.type ? [f.type] : undefined, failedOnly: f.failedOnly || undefined,
    }, p, extra);
  }, []);
  const grid = useRangeGrid<AuditRow>(fetchRange);

  // 조건을 바꾸면 처음부터(첫 그리기는 그리드가 스스로 읽는다)
  const firstRef = useRef(true);
  useEffect(() => {
    if (firstRef.current) { firstRef.current = false; return; }
    setSelection({ type: 'include', ids: new Set() });
    grid.refresh();
  }, [applied]); // eslint-disable-line react-hooks/exhaustive-deps

  const search = () => setApplied({ ...filter });

  const columns = useMemo<GridColDef<AuditRow>[]>(() => [
    { field: 'id', headerName: 'ID', width: 90 },
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'messageType', headerName: t('i18n.tc.audit.type', '종류'), width: 110,
      renderCell: ({ row }) => (row.messageType
        ? <Chip size="small" variant={isFailed(row) ? 'filled' : 'outlined'} color={isFailed(row) ? 'error' : (TYPE_COLOR[row.messageType] ?? 'default')} label={row.messageType} />
        : null),
    },
    { field: 'name', headerName: 'opcode', width: 220 },
    { field: 'transactionId', headerName: t('i18n.tc.audit.transactionId', '트랜잭션 ID'), width: 220 },
    { field: 'route', headerName: t('i18n.tc.audit.route', '보낸 곳 → 받는 곳'), width: 220, valueGetter: (_v, r) => (r.id ? `${r.sender ?? ''} → ${r.receiver ?? ''}` : '') },
    { field: 'principal', headerName: t('i18n.tc.audit.principal', '호출자'), width: 140 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 220 },
    // 작업 칸(오른쪽 고정) — 그 한 줄을 결과 재전송. 다시 보낼 수 있는 기록(전달·응답·오류)만, 관리자만
    ...(canRedeliver ? [{
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 70,
      getActions: ({ row }) => (REDELIVERABLE.includes(row.messageType)
        ? [<GridActionsCellItem key="redeliver" icon={<SendOutlinedIcon />} label={t('i18n.tc.redeliver.title', '결과 재전송')}
          onClick={() => setRedeliver([row])} />]
        : []),
    } as GridColDef<AuditRow>] : []),
  ], [t, canRedeliver]);

  const selectedRows = (): AuditRow[] => [...(grid.apiRef.current?.getSelectedRows().values() ?? [])] as AuditRow[];

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => grid.refresh()} />
        <ScreenAction kind="excelDown" onClick={() => setExporting(true)} disabled={!grid.total} />
        {canRedeliver && (
          // 새로 고침(↻)과 헷갈리지 않게 보내기 아이콘 — 표의 작업 칸과 같은 아이콘
          <ScreenAction icon={<SendOutlinedIcon fontSize="small" />} label={t('i18n.tc.redeliver.title', '결과 재전송')}
            onClick={() => setRedeliver(selectedRows())} />
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
      {canRedeliver && selection.ids.size > 0 && (
        <Typography variant="caption" color="text.secondary">{t('i18n.tc.audit.selected', '고른 기록')} {selection.ids.size}</Typography>
      )}
      <RangeGrid grid={grid} title={t('i18n.tc.audit.listTitle', '송수신 목록')} columns={columns} getRowId={(r) => r.id}
        gridProps={{
          checkboxSelection: canRedeliver, rowSelectionModel: selection, onRowSelectionModelChange: setSelection,
          onRowClick: (p) => { if (p.row.transactionId) setDetailOf(p.row.transactionId); },
          sx: { '& .MuiDataGrid-row': { cursor: 'pointer' } },
        }} />
      <AuditDetailDialog transactionId={detailOf} onClose={() => setDetailOf(null)} />
      <RedeliverDialog rows={redeliver} onClose={() => setRedeliver(null)} onDone={() => setSelection({ type: 'include', ids: new Set() })} />
      <RangeExportDialog open={exporting} onClose={() => setExporting(false)} grid={grid}
        fileName={t('i18n.tc.audit.excelFile', '송수신기록')} sheetName={t('i18n.tc.audit.listTitle', '송수신 목록')}
        options={[{ key: 'includeBody', label: t('i18n.tc.audit.excelBody', '본문(JSON) 포함'), help: t('i18n.tc.audit.excelBodyHelp', '파일이 아주 커질 수 있습니다') }]}
        toRow={(r, no) => ({
          [t('i18n.tc.schedule.no', '번호')]: no, ID: r.id, [t('i18n.tc.audit.time', '시각')]: fmtTime(r.timestamp),
          [t('i18n.tc.audit.type', '종류')]: r.messageType, opcode: r.name, [t('i18n.tc.audit.transactionId', '트랜잭션 ID')]: r.transactionId,
          [t('i18n.tc.audit.sender', '보낸 곳')]: r.sender ?? '', [t('i18n.tc.audit.receiver', '받는 곳')]: r.receiver ?? '',
          [t('i18n.tc.audit.principal', '호출자')]: r.principal ?? '', [t('i18n.tc.common.description', '설명')]: r.description ?? '',
          ...(r.dataMap && Object.keys(r.dataMap).length ? { [t('i18n.tc.audit.body', '본문')]: JSON.stringify(r.dataMap) } : {}),
        })} />
    </PageContainer>
  );
}
