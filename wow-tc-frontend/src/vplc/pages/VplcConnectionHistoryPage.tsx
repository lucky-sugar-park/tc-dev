import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Chip, IconButton, InputAdornment, MenuItem, TextField } from '@mui/material';
import { type GridColDef } from '@mui/x-data-grid-pro';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { CommandBarActions, FilterBar, PageContainer, ScreenAction, useI18n } from 'wow-mes-frontend-shared';
import { RangeExportDialog, RangeGrid, useRangeGrid, type RangeParams } from '../../shared/components/RangeGrid';
import { fmtTime } from '../../shared/format';
import { PROTOCOL_COLOR, PROTOCOL_LABEL, vplcApi } from '../services/vplcApi';
import type { VplcConnectionHistory, VplcProtocol } from '../types/vplc';

const PROTOCOLS: VplcProtocol[] = ['MELSEC_MC', 'MODBUS_TCP', 'OPC_UA'];

/** datetime-local 값(이 브라우저 시각) → ISO */
const fromLocalInput = (s: string) => (s ? new Date(s).toISOString() : undefined);

type Filter = { search: string; protocol: '' | VplcProtocol; from: string; to: string };

const initialFilter = (): Filter => ({ search: '', protocol: '', from: '', to: '' });

/** 연결·끊김 칩 */
const EVENT_COLOR: Record<string, 'success' | 'default' | 'warning'> = { CONNECTED: 'success', DISCONNECTED: 'default', VPLC_RELEASED_BY_FORCED: 'warning' };

/**
 * 가상 PLC 연결 이력 — 가상 PLC 에 클라이언트(tc 커넥터 등)가 붙고 끊긴 기록(CONNECTED_CLIENT_HIST). 가상 PLC 이름·클라이언트 IP, 종류,
 * 연결 기간으로 찾는다. 구간 스크롤 표(docs/range-grid-design.md): 최신순, 스크롤하면 그 구간을 받고, 10만 건 넘으면 구간 이동.
 * 지운 가상 PLC 의 이력은 이름 대신 아이디가 나오고 IP 로만 찾는다.
 */
export default function VplcConnectionHistoryPage(): ReactElement {
  const { t } = useI18n();
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const appliedRef = useRef(applied);
  appliedRef.current = applied;
  const [exporting, setExporting] = useState(false);

  const fetchRange = useCallback((p: RangeParams) => {
    const f = appliedRef.current;
    return vplcApi.connectionHistoryRange({
      search: f.search.trim() || undefined, protocol: f.protocol || undefined, from: fromLocalInput(f.from), to: fromLocalInput(f.to),
    }, p);
  }, []);
  const grid = useRangeGrid<VplcConnectionHistory>(fetchRange);

  const firstRef = useRef(true);
  useEffect(() => {
    if (firstRef.current) { firstRef.current = false; return; }
    grid.refresh();
  }, [applied]); // eslint-disable-line react-hooks/exhaustive-deps

  const search = () => setApplied({ ...filter });
  const reset = () => { const f = initialFilter(); setFilter(f); setApplied(f); };

  const eventLabel = useCallback((e: string) => (e === 'CONNECTED' ? t('i18n.tc.vplc.event.connected', '연결')
    : e === 'DISCONNECTED' ? t('i18n.tc.vplc.event.disconnected', '끊김')
    : e === 'VPLC_RELEASED_BY_FORCED' ? t('i18n.tc.vplc.event.released', '강제 해제') : e), [t]);

  const columns = useMemo<GridColDef<VplcConnectionHistory>[]>(() => [
    { field: 'timestamp', headerName: t('i18n.tc.vplc.eventTime', '시각'), width: 170, sortable: false, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'vplcName', headerName: t('i18n.tc.vplc.vplc', '가상 PLC'), width: 180, sortable: false, valueGetter: (v, r) => v ?? r.vplcId },
    {
      field: 'protocol', headerName: t('i18n.tc.vplc.kind', '종류'), width: 120, sortable: false,
      renderCell: ({ row }) => (row.protocol ? <Chip size="small" variant="outlined" color={PROTOCOL_COLOR[row.protocol]} label={PROTOCOL_LABEL[row.protocol]} /> : ''),
    },
    { field: 'port', headerName: t('i18n.tc.vplc.port', '포트'), width: 90, type: 'number', sortable: false, valueFormatter: (v: number) => String(v) },
    { field: 'clientIp', headerName: t('i18n.tc.vplc.clientIp', '클라이언트 IP'), width: 150, sortable: false },
    {
      field: 'connOrDisconn', headerName: t('i18n.tc.vplc.event', '연결·끊김'), width: 110, sortable: false,
      renderCell: ({ row }) => (row.connOrDisconn ? <Chip size="small" color={EVENT_COLOR[row.connOrDisconn] ?? 'default'} label={eventLabel(row.connOrDisconn)} /> : null),
    },
    { field: 'clientChannelId', headerName: t('i18n.tc.vplc.channel', '채널'), width: 200, sortable: false },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 200, sortable: false },
  ], [t, eventLabel]);

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => grid.refresh()} />
        <ScreenAction kind="excelDown" onClick={() => setExporting(true)} disabled={!grid.total} />
      </CommandBarActions>
      <FilterBar onSearch={search} onReset={reset}>
        <TextField size="small" placeholder={t('i18n.tc.vplc.histSearch', '가상 PLC 이름·IP')} value={filter.search}
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
        <TextField select size="small" label={t('i18n.tc.vplc.kindOfVplc', '가상 PLC 종류')} value={filter.protocol} sx={{ width: 150 }}
          onChange={(e) => setFilter({ ...filter, protocol: e.target.value as Filter['protocol'] })}
          slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.vplc.kindOfVplc', '가상 PLC 종류') }, inputLabel: { shrink: true }, select: { displayEmpty: true } }}>
          <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
          {PROTOCOLS.map((p) => <MenuItem key={p} value={p}>{PROTOCOL_LABEL[p]}</MenuItem>)}
        </TextField>
        <TextField size="small" type="datetime-local" label={t('i18n.tc.vplc.connFrom', '연결 기간 시작')} value={filter.from}
          onChange={(e) => setFilter({ ...filter, from: e.target.value })}
          slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.vplc.connFrom', '연결 기간 시작') }, inputLabel: { shrink: true } }} />
        <TextField size="small" type="datetime-local" label={t('i18n.tc.vplc.connTo', '연결 기간 끝')} value={filter.to}
          onChange={(e) => setFilter({ ...filter, to: e.target.value })}
          slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.vplc.connTo', '연결 기간 끝') }, inputLabel: { shrink: true } }} />
      </FilterBar>
      <RangeGrid grid={grid} title={t('i18n.tc.vplc.connHistTitle', '가상 PLC 연결 이력')} columns={columns} getRowId={(r) => r.seqNo} />
      <RangeExportDialog open={exporting} onClose={() => setExporting(false)} grid={grid}
        fileName={t('i18n.tc.vplc.connHistExcelFile', '가상PLC연결이력')} sheetName={t('i18n.tc.vplc.connHistTitle', '가상 PLC 연결 이력')}
        toRow={(r, no) => ({
          [t('i18n.tc.schedule.no', '번호')]: no, [t('i18n.tc.vplc.eventTime', '시각')]: fmtTime(r.timestamp),
          [t('i18n.tc.vplc.vplc', '가상 PLC')]: r.vplcName ?? r.vplcId, [t('i18n.tc.vplc.kind', '종류')]: r.protocol ? PROTOCOL_LABEL[r.protocol] : '',
          [t('i18n.tc.vplc.port', '포트')]: r.port, [t('i18n.tc.vplc.clientIp', '클라이언트 IP')]: r.clientIp ?? '',
          [t('i18n.tc.vplc.event', '연결·끊김')]: eventLabel(r.connOrDisconn), [t('i18n.tc.vplc.channel', '채널')]: r.clientChannelId ?? '',
          [t('i18n.tc.common.description', '설명')]: r.description ?? '',
        })} />
    </PageContainer>
  );
}
