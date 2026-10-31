import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Tooltip, Typography } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
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
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { ConnectorFormDialog } from '../components/ConnectorFormDialog';
import { ConnectionHistoryDialog } from '../components/ConnectionHistoryDialog';
import { TestReadDialog } from '../components/TestReadDialog';
import { usePlcConnectionEvents } from '../hooks/usePlcConnectionEvents';
import { connectorApi, fetchProtocols } from '../services/plcApi';
import type { PlcConnectionState, PlcConnectorInfo, ProtocolDescriptor } from '../types/plc';

const SCREEN = 'tc.plc.connector';
/** 연결 상태를 다시 읽는 간격 — SSE 는 PLC 를 연결한 인스턴스에서만 오므로 함께 쓴다 */
const STATE_POLL_MS = 10_000;

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

  const columns = useMemo<GridColDef<PlcConnectorInfo>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.connector.name', '커넥터'), width: 160 },
    { field: 'protocol', headerName: t('i18n.tc.connector.protocol', '프로토콜'), width: 110 },
    {
      field: 'endpoint', headerName: t('i18n.tc.connector.endpoint', 'PLC 주소:포트'), width: 190,
      valueGetter: (_v, r) => `${r.hostIp}:${(r.ports ?? []).join(',')}`,
    },
    {
      field: 'frame', headerName: t('i18n.tc.connector.frame', '프레임·장치'), width: 120,
      valueGetter: (_v, r) => String(r.settings?.frame ?? (r.settings?.unitId != null ? `unit ${r.settings.unitId}` : '')),
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
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), width: 180 },
    {
      field: 'actions', type: 'actions', headerName: '', width: 130,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [];
        if (perm('U')) acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />);
        if (perm('X')) {
          acts.push(row.published
            ? <GridActionsCellItem key="release" icon={<StopIcon />} label={t('i18n.tc.connector.release', '해제')} onClick={() => control('release', row)} />
            : <GridActionsCellItem key="publish" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.publish', '게시')} onClick={() => control('publish', row)} />);
          if (row.published) {
            acts.push(row.connection?.operStatus === 'PAUSED'
              ? <GridActionsCellItem key="resume" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => control('resume', row)} showInMenu />
              : <GridActionsCellItem key="pause" icon={<PauseIcon />} label={t('i18n.tc.connector.pause', '멈춤')} onClick={() => control('pause', row)} showInMenu />);
          }
          acts.push(<GridActionsCellItem key="test" icon={<NetworkCheckIcon />} label={t('i18n.tc.connector.testConnection', '연결 시험')} onClick={() => testConnection(row)} showInMenu />);
          acts.push(<GridActionsCellItem key="read" icon={<ScienceIcon />} label={t('i18n.tc.testRead.title', '시험 읽기')} onClick={() => setTestReadOf(row)} disabled={!row.published} showInMenu />);
        }
        acts.push(<GridActionsCellItem key="hist" icon={<HistoryIcon />} label={t('i18n.tc.history.title', '연결 이력')} onClick={() => setHistoryOf(row.name)} showInMenu />);
        if (perm('D')) acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />);
        return acts;
      },
    },
  ], [t, perm, control, testConnection, remove]);

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
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']}
          isInactive={(r) => !r.published} />
      </Box>
      <ConnectorFormDialog open={form.open} initial={form.initial} protocols={protocols} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(info) => { void save(info); }} />
      <ConnectionHistoryDialog name={historyOf} onClose={() => setHistoryOf(null)} />
      <TestReadDialog name={testReadOf?.name ?? null} protocol={testReadOf ? protocolOf(testReadOf.protocol) : undefined}
        onClose={() => setTestReadOf(null)} />
    </PageContainer>
  );
}
