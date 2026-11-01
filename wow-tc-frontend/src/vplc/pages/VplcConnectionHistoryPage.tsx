import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, IconButton, InputAdornment, MenuItem, TextField } from '@mui/material';
import { DataGridPro, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid-pro';
import SearchIcon from '@mui/icons-material/Search';
import ClearIcon from '@mui/icons-material/Clear';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { CommandBarActions, FilterBar, LoadErrorState, PageContainer, ScreenAction, extractApiError, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListTitle } from '../../shared/components/ListTitle';
import { fmtTime } from '../../shared/format';
import { PROTOCOL_COLOR, PROTOCOL_LABEL, vplcApi } from '../services/vplcApi';
import type { VplcConnectionHistory, VplcProtocol } from '../types/vplc';

const PROTOCOLS: VplcProtocol[] = ['MELSEC_MC', 'MODBUS_TCP', 'OPC_UA'];

/** datetime-local 값(이 브라우저 시각) → ISO */
const fromLocalInput = (s: string) => (s ? new Date(s).toISOString() : undefined);

type Filter = { search: string; protocol: '' | VplcProtocol; from: string; to: string };

const initialFilter = (): Filter => ({ search: '', protocol: '', from: '', to: '' });

type Row = VplcConnectionHistory & { no: number };

/** 연결·끊김 칩 */
const EVENT_COLOR: Record<string, 'success' | 'default' | 'warning'> = { CONNECTED: 'success', DISCONNECTED: 'default', VPLC_RELEASED_BY_FORCED: 'warning' };

/**
 * 가상 PLC 연결 이력 — 가상 PLC 에 클라이언트(tc 커넥터 등)가 붙고 끊긴 기록(CONNECTED_CLIENT_HIST). 가상 PLC 이름·클라이언트 IP, 종류,
 * 연결 기간으로 찾는다(서버 쪽 쪽 넘김, 최신순). 지운 가상 PLC 의 이력은 이름 대신 아이디가 나오고 IP 로만 찾는다.
 */
export default function VplcConnectionHistoryPage(): ReactElement {
  const { t } = useI18n();
  const active = useIsActiveScreen();
  const [filter, setFilter] = useState<Filter>(initialFilter);
  const [applied, setApplied] = useState<Filter>(filter);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: 100 });
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const p = await vplcApi.connectionHistories({
        search: applied.search.trim() || undefined, protocol: applied.protocol || undefined,
        from: fromLocalInput(applied.from), to: fromLocalInput(applied.to), page: page.page, size: page.pageSize,
      });
      setRows(p.items.map((r, i) => ({ ...r, no: page.page * page.pageSize + i + 1 })));
      setTotal(p.total);
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [applied, page, fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { if (active) void loadRef.current(); }, [active, applied, page]);

  const search = () => { setPage((p) => ({ ...p, page: 0 })); setApplied({ ...filter }); };
  const reset = () => { const f = initialFilter(); setFilter(f); setPage((p) => ({ ...p, page: 0 })); setApplied(f); };

  const eventLabel = useCallback((e: string) => (e === 'CONNECTED' ? t('i18n.tc.vplc.event.connected', '연결')
    : e === 'DISCONNECTED' ? t('i18n.tc.vplc.event.disconnected', '끊김')
    : e === 'VPLC_RELEASED_BY_FORCED' ? t('i18n.tc.vplc.event.released', '강제 해제') : e), [t]);

  const columns = useMemo<GridColDef<Row>[]>(() => [
    { field: 'no', headerName: t('i18n.tc.schedule.no', '번호'), width: 80, type: 'number', sortable: false },
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
      renderCell: ({ row }) => <Chip size="small" color={EVENT_COLOR[row.connOrDisconn] ?? 'default'} label={eventLabel(row.connOrDisconn)} />,
    },
    { field: 'clientChannelId', headerName: t('i18n.tc.vplc.channel', '채널'), width: 200, sortable: false },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 200, sortable: false },
  ], [t, eventLabel]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
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
      <ListTitle title={t('i18n.tc.vplc.connHistTitle', '가상 PLC 연결 이력')} count={total} />
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={rows} columns={columns} getRowId={(r) => r.seqNo} loading={loading} density="compact"
            showColumnVerticalBorder showCellVerticalBorder disableRowSelectionOnClick
            pagination paginationMode="server" rowCount={total} paginationModel={page} onPaginationModelChange={setPage}
            pageSizeOptions={[50, 100, 200, 500]}
            slotProps={{ basePagination: { showFirstButton: true, showLastButton: true } as never }}
            getRowClassName={(p) => (p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd')} />
        </Box>
      </Box>
    </PageContainer>
  );
}
