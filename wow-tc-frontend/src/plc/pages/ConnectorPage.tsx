import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { GridActionsCellItem, type GridColDef, type GridPaginationModel } from '@mui/x-data-grid-pro';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import ViewListIcon from '@mui/icons-material/ViewList';
import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import PauseIcon from '@mui/icons-material/Pause';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import ScienceIcon from '@mui/icons-material/Science';
import HistoryIcon from '@mui/icons-material/History';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  LoadErrorState, PageContainer, downloadExcel, excelErrorMessage, extractApiError, fileStamp, showAlert, showConfirm, showToast, useI18n,
  useLoadFailure,
} from 'wow-mes-frontend-shared';
import { ListTitle } from '../../shared/components/ListTitle';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { PAGE_SIZE_OPTIONS, StandardGrid, TopPagination } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { ConnectorCards, type ConnectorAction } from '../components/ConnectorCards';
import { ConnectorFormDialog } from '../components/ConnectorFormDialog';
import { ConnectionHistoryDialog } from '../components/ConnectionHistoryDialog';
import { TestReadDialog } from '../components/TestReadDialog';
import { usePlcConnectionEvents } from '../hooks/usePlcConnectionEvents';
import { connectorApi, fetchProtocols } from '../services/plcApi';
import type { PlcConnectionState, PlcConnectorInfo, ProtocolDescriptor } from '../types/plc';

const SCREEN = 'tc.plc.connector';
/** 연결 상태를 다시 읽는 간격 — SSE 는 PLC 를 연결한 인스턴스에서만 오므로 함께 쓴다 */
const STATE_POLL_MS = 10_000;
/** 카드·표 중 고른 보기 — 이 브라우저에만 기억한다 */
const VIEW_KEY = 'tc.plc.connector.view';
type View = 'card' | 'table';

function loadView(): View {
  try {
    return localStorage.getItem(VIEW_KEY) === 'card' ? 'card' : 'table';
  } catch {
    return 'table';
  }
}

/** 포트 칸 입력(쉼표로 나눔) → 포트 목록. 잘못되면 오류 */
function parsePorts(text: string): number[] {
  const ports = text.split(/[,\s]+/).filter(Boolean).map(Number);
  if (ports.length === 0 || ports.some((p) => !Number.isInteger(p) || p < 1 || p > 65535)) {
    throw new Error(`포트는 1~65535 숫자를 쉼표로 나눠 적습니다: ${text}`);
  }
  return ports;
}

/** 연결 상태 표시 */
export function ConnectionChip({ info }: { info: { published?: boolean; connection?: PlcConnectionState | null } }): ReactElement {
  const { t } = useI18n();
  const c = info.connection;
  if (!info.published) return <Chip size="small" label={t('i18n.tc.connector.released', '해제')} />;
  if (!c) return <Chip size="small" color="warning" label={t('i18n.tc.connector.noState', '상태 없음')} />;
  if (c.operStatus === 'PAUSED') return <Chip size="small" color="warning" label={t('i18n.tc.connector.paused', '멈춤')} />;
  const up = c.activeChannels > 0;
  return (
    <Chip size="small" color={up ? 'success' : 'error'}
      label={up ? `${t('i18n.tc.connector.connected', '연결')} ${c.activeChannels}` : t('i18n.tc.connector.disconnected', '끊김')} />
  );
}

/**
 * PLC 커넥터 (docs/frontend-design.md §5) — 목록·연결 상태(SSE + 10초 조회), 등록·수정(프로토콜별 설정), 게시·해제·멈춤·재개,
 * 포트별 연결 시험, 시험 읽기, 연결 이력, 삭제. 버튼은 화면 권한(uiroute.tc.plc.connector)으로 숨는다.
 */
export default function ConnectorPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<PlcConnectorInfo[]>([]);
  const [protocols, setProtocols] = useState<ProtocolDescriptor[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: PlcConnectorInfo; error?: string | null }>({ open: false });
  const [historyOf, setHistoryOf] = useState<string | null>(null);
  const [testReadOf, setTestReadOf] = useState<PlcConnectorInfo | null>(null);
  const [view, setView] = useState<View>(loadView);
  const [page, setPage] = useState<GridPaginationModel>({ page: 0, pageSize: PAGE_SIZE_OPTIONS[0] });
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, protos] = await Promise.all([connectorApi.list(), fetchProtocols()]);
      setRows(list);
      setProtocols(protos);
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;

  /** 연결 상태만 다시 — 목록은 그대로 */
  const refreshStates = useCallback(async () => {
    try {
      const states = await connectorApi.states();
      const by = new Map(states.map((s) => [s.plcConnectorName, s]));
      setRows((rs) => rs.map((r) => ({ ...r, connection: by.get(r.name) ?? null })));
    } catch {
      // 다음 주기에
    }
  }, []);

  useEffect(() => { if (active) void loadRef.current(); }, [active]);
  useEffect(() => {
    if (!active) return undefined;
    const id = window.setInterval(() => { void refreshStates(); }, STATE_POLL_MS);
    return () => window.clearInterval(id);
  }, [active, refreshStates]);
  usePlcConnectionEvents(() => { void refreshStates(); }, active);

  const protocolOf = useCallback((p: string) => protocols.find((d) => d.protocol === p), [protocols]);

  const run = useCallback(async (label: string, fn: () => Promise<unknown>, reload = true) => {
    try {
      await fn();
      showToast(label, 'success');
      if (reload) await loadRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, []);

  const control = useCallback((op: 'publish' | 'release' | 'pause' | 'resume', r: PlcConnectorInfo) => {
    const labels = {
      publish: t('i18n.tc.connector.publish', '게시'), release: t('i18n.tc.connector.release', '해제'),
      pause: t('i18n.tc.connector.pause', '멈춤'), resume: t('i18n.tc.connector.resume', '재개'),
    };
    void (async () => {
      if (op === 'release' || op === 'pause') {
        const ok = await showConfirm({
          message: t('i18n.tc.connector.confirmStop', '{name} 을(를) {op} 하면 이 PLC 로 가는 요청이 실패합니다. 계속할까요?')
            .replace('{name}', r.name).replace('{op}', labels[op]),
          severity: 'warning',
        });
        if (!ok) return;
      }
      await run(`${r.name} ${labels[op]}`, () => connectorApi.control(op, r.name));
    })();
  }, [run, t]);

  const testConnection = useCallback((r: PlcConnectorInfo) => {
    void (async () => {
      try {
        const res = await connectorApi.testConnection(r.name);
        const ports = Object.entries(res).filter(([k]) => k.startsWith('port:')).map(([k, v]) => `${k.slice(5)}: ${v}`);
        const ok = res.result === 'CONNECTION_TEST_OK';
        showAlert({
          severity: ok ? 'success' : 'warning',
          title: `${t('i18n.tc.connector.testConnection', '연결 시험')} — ${r.name}`,
          message: [...ports, res.description ?? ''].filter(Boolean).join('\n'),
        });
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const remove = useCallback((r: PlcConnectorInfo) => {
    void (async () => {
      const ok = await showConfirm({
        message: t('i18n.tc.connector.confirmDelete', '{name} 을(를) 지울까요? 이 커넥터를 쓰는 인터페이스는 요청이 실패합니다.').replace('{name}', r.name),
        severity: 'error',
      });
      if (ok) await run(`${r.name} ${t('i18n.common.delete', '삭제')}`, () => connectorApi.remove(r.name));
    })();
  }, [run, t]);

  const save = async (info: PlcConnectorInfo) => {
    try {
      if (form.initial) await connectorApi.update(info); else await connectorApi.register(info);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, r.hostIp, r.description, r.protocol)), [rows, search]);
  // 검색으로 줄어 지금 쪽이 비면 첫 쪽으로
  useEffect(() => { setPage((p) => (p.page * p.pageSize >= filtered.length && p.page > 0 ? { ...p, page: 0 } : p)); }, [filtered.length]);

  const changeView = (v: View | null) => {
    if (!v) return;
    setView(v);
    try { localStorage.setItem(VIEW_KEY, v); } catch { /* 기억하지 못해도 된다 */ }
  };

  /** 셀 편집 저장 — 행 전체를 수정 API 로. 게시 중이면 다시 게시해야 연결에 반영된다 */
  const saveRow = useCallback(async (next: PlcConnectorInfo, prev: PlcConnectorInfo) => {
    if (next.hostIp === prev.hostIp && next.description === prev.description
      && (next.ports ?? []).join(',') === (prev.ports ?? []).join(',')) return prev;
    if (!next.hostIp?.trim()) throw new Error(t('i18n.tc.connector.hostRequired', 'PLC 주소를 적어 주세요'));
    if ((next.ports ?? []).some((p) => !Number.isInteger(p))) throw new Error(t('i18n.tc.connector.portsInvalid', '포트는 1~65535 숫자를 쉼표로 나눠 적습니다'));
    const { connection, ...info } = next;
    await connectorApi.update({ ...info, hostIp: info.hostIp.trim() });
    showToast(prev.published
      ? t('i18n.tc.connector.savedRepublish', '저장했습니다 — 게시 중인 커넥터는 해제 후 다시 게시해야 연결에 반영됩니다')
      : t('i18n.common.saved', '저장했습니다'), prev.published ? 'warning' : 'success');
    setRows((rs) => rs.map((r) => (r.name === next.name ? { ...next, connection } : r)));
    return { ...next, connection };
  }, [t]);

  /** 표의 작업 칸과 카드 아래 버튼이 함께 쓴다 */
  const actionsFor = useCallback((row: PlcConnectorInfo): ConnectorAction[] => {
    const acts: ConnectorAction[] = [];
    if (perm('U')) acts.push({ key: 'edit', icon: <EditIcon fontSize="small" />, label: t('i18n.common.edit', '수정'), onClick: () => setForm({ open: true, initial: row }) });
    if (perm('X')) {
      acts.push(row.published
        ? { key: 'release', icon: <StopIcon fontSize="small" />, label: t('i18n.tc.connector.release', '해제'), onClick: () => control('release', row) }
        : { key: 'publish', icon: <PlayArrowIcon fontSize="small" />, label: t('i18n.tc.connector.publish', '게시'), onClick: () => control('publish', row) });
      if (row.published) {
        acts.push(row.connection?.operStatus === 'PAUSED'
          ? { key: 'resume', icon: <PlayArrowIcon fontSize="small" />, label: t('i18n.tc.connector.resume', '재개'), onClick: () => control('resume', row), menu: true }
          : { key: 'pause', icon: <PauseIcon fontSize="small" />, label: t('i18n.tc.connector.pause', '멈춤'), onClick: () => control('pause', row), menu: true });
      }
      acts.push({ key: 'test', icon: <NetworkCheckIcon fontSize="small" />, label: t('i18n.tc.connector.testConnection', '연결 시험'), onClick: () => testConnection(row), menu: true });
      acts.push({ key: 'read', icon: <ScienceIcon fontSize="small" />, label: t('i18n.tc.testRead.title', '시험 읽기'), onClick: () => setTestReadOf(row), disabled: !row.published, menu: true });
    }
    acts.push({ key: 'hist', icon: <HistoryIcon fontSize="small" />, label: t('i18n.tc.history.title', '연결 이력'), onClick: () => setHistoryOf(row.name), menu: true });
    if (perm('D')) acts.push({ key: 'del', icon: <DeleteOutlineIcon fontSize="small" />, label: t('i18n.common.delete', '삭제'), onClick: () => remove(row), menu: true });
    return acts;
  }, [t, perm, control, testConnection, remove]);

  const editable = perm('U');
  const columns = useMemo<GridColDef<PlcConnectorInfo>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.connector.name', '커넥터'), width: 160 },
    { field: 'protocol', headerName: t('i18n.tc.connector.protocol', '프로토콜'), width: 110 },
    { field: 'hostIp', headerName: t('i18n.tc.connector.host', 'PLC 주소'), width: 140, editable },
    {
      field: 'ports', headerName: t('i18n.tc.connector.ports', '포트'), width: 110, editable,
      valueGetter: (_v, r) => (r.ports ?? []).join(','),
      // valueSetter 는 렌더 중에 불려 예외를 던지면 화면이 죽는다 — 잘못된 값은 표시만 해 두고 저장(saveRow)이 거절한다
      valueSetter: (value: string, r) => {
        try { return { ...r, ports: parsePorts(String(value)) }; } catch { return { ...r, ports: [Number.NaN] }; }
      },
    },
    {
      field: 'frame', headerName: t('i18n.tc.connector.frame', '프레임·장치'), width: 120,
      valueGetter: (_v, r) => String(r.settings?.frame ?? (r.settings?.unitId != null ? `unit ${r.settings.unitId}` : '')),
    },
    {
      field: 'published', headerName: t('i18n.tc.connector.publishStatus', '발행 상태'), width: 120,
      valueGetter: (_v, r) => (r.published ? 'PUBLISHED' : 'RELEASED'),
      renderCell: ({ row }) => (
        <Tooltip title={row.published ? '' : t('i18n.tc.connector.releasedHelp', '게시(PUBLISHED)해야 PLC 에 연결합니다')}>
          <Chip size="small" color={row.published ? 'primary' : 'default'} variant={row.published ? 'filled' : 'outlined'}
            label={row.published ? 'PUBLISHED' : 'RELEASED'} />
        </Tooltip>
      ),
    },
    {
      field: 'state', headerName: t('i18n.tc.connector.state', '연결 상태'), width: 110, sortable: false,
      renderCell: ({ row }) => <ConnectionChip info={row} />,
    },
    { field: 'instance', headerName: t('i18n.tc.connector.instance', '연결 인스턴스'), width: 190, valueGetter: (_v, r) => r.connection?.instance ?? '' },
    {
      field: 'lastError', headerName: t('i18n.tc.connector.lastError', '마지막 오류'), flex: 1, minWidth: 200,
      valueGetter: (_v, r) => r.connection?.lastError ?? '',
      renderCell: ({ row }) => row.connection?.lastError ? (
        <Tooltip title={`${fmtTime(row.connection.lastErrorAt)} ${row.connection.lastError}`}>
          <Typography variant="body2" noWrap sx={{ color: 'error.main' }}>{row.connection.lastError}</Typography>
        </Tooltip>
      ) : null,
    },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), width: 180, editable },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 100,
      getActions: ({ row }) => actionsFor(row).map((a) => (
        <GridActionsCellItem key={a.key} icon={a.icon} label={a.label} onClick={a.onClick} disabled={a.disabled} showInMenu={a.menu} />
      )),
    },
  ], [t, editable, actionsFor]);

  const cardRows = filtered.slice(page.page * page.pageSize, (page.page + 1) * page.pageSize);

  const exportExcel = () => {
    downloadExcel({
      fileName: `plc-connectors_${fileStamp()}`,
      sheets: [{
        name: t('i18n.menu.tc.plc.connector', '커넥터'),
        rows: filtered.map((r) => ({
          [t('i18n.tc.connector.name', '커넥터')]: r.name,
          [t('i18n.tc.connector.protocol', '프로토콜')]: r.protocol,
          [t('i18n.tc.connector.host', 'PLC 주소')]: r.hostIp,
          [t('i18n.tc.connector.ports', '포트')]: (r.ports ?? []).join(','),
          [t('i18n.tc.connector.published', '게시')]: r.published ? 'Y' : 'N',
          [t('i18n.tc.connector.channels', '연결 수')]: r.connection?.activeChannels ?? '',
          [t('i18n.tc.connector.lastError', '마지막 오류')]: r.connection?.lastError ?? '',
          [t('i18n.tc.common.description', '설명')]: r.description ?? '',
        })),
      }],
    }).catch((e) => showToast(excelErrorMessage(e), 'error'));
  };

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} onExport={exportExcel}
        placeholder={t('i18n.tc.connector.search', '이름·주소·프로토콜')} />
      <ListTitle title={t('i18n.tc.connector.listTitle', 'PLC 커넥터 목록')} count={filtered.length}>
        <ToggleButtonGroup size="small" exclusive value={view} onChange={(_e, v: View | null) => changeView(v)}>
          <ToggleButton value="card" aria-label={t('i18n.tc.connector.viewCard', '카드로 보기')}>
            <Tooltip title={t('i18n.tc.connector.viewCard', '카드로 보기')}><ViewModuleIcon fontSize="small" /></Tooltip>
          </ToggleButton>
          <ToggleButton value="table" aria-label={t('i18n.tc.connector.viewTable', '표로 보기')}>
            <Tooltip title={t('i18n.tc.connector.viewTable', '표로 보기')}><ViewListIcon fontSize="small" /></Tooltip>
          </ToggleButton>
        </ToggleButtonGroup>
        {/* 쪽 넘김 — 카드·표가 같은 쪽을 쓴다 */}
        <TopPagination count={filtered.length} model={page} onChange={setPage} />
      </ListTitle>
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        {view === 'table' ? (
          <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']}
            isInactive={(r) => !r.published} pagination
            gridProps={{
              paginationModel: page, onPaginationModelChange: setPage, hideFooter: true,
              processRowUpdate: saveRow,
              onProcessRowUpdateError: (e: unknown) => showToast(e instanceof Error && !('response' in e) ? e.message : extractApiError(e), 'error'),
            }} />
        ) : (
          <>
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
              <ConnectorCards rows={cardRows} actionsFor={actionsFor} selected={selectedCard} onSelect={setSelectedCard} />
            </Box>
          </>
        )}
      </Box>
      <ConnectorFormDialog open={form.open} initial={form.initial} protocols={protocols} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(info) => { void save(info); }} />
      <ConnectionHistoryDialog name={historyOf} onClose={() => setHistoryOf(null)} />
      <TestReadDialog name={testReadOf?.name ?? null} protocol={testReadOf ? protocolOf(testReadOf.protocol) : undefined}
        onClose={() => setTestReadOf(null)} />
    </PageContainer>
  );
}
