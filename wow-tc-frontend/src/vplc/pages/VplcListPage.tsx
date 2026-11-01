import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Link } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import { useNavigate } from 'react-router-dom';
import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import PauseIcon from '@mui/icons-material/Pause';
import MemoryIcon from '@mui/icons-material/Memory';
import CableIcon from '@mui/icons-material/Cable';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import TimerIcon from '@mui/icons-material/Timer';
import EventAvailableIcon from '@mui/icons-material/EventAvailable';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { DelayDialog, VplcConnectionsDialog, VplcFormDialog } from '../components/VplcDialogs';
import { PROTOCOL_COLOR, PROTOCOL_LABEL, STATUS_COLOR, isPublished, protocolOf, vplcApi, type VplcControl } from '../services/vplcApi';
import type { Vplc, VplcProtocol } from '../types/vplc';

const SCREEN = 'tc.vplc.list';


/**
 * 가상 PLC 목록 (docs/frontend-design.md §5, docs/virtual-devices.md) — 한 vplc 가 띄우는 MELSEC·Modbus·OPC UA 가상 PLC. 종류와 관계없이 같은
 * 동작 — 등록·수정, 게시·해제, 시작·정지·일시정지(무응답)·재개, 포트별 연결·연결 끊기, 메모리(태그) 화면으로, 삭제. Modbus 는 응답 지연을 더 바꾼다.
 * 화면이 vplc 를 바로 부른다(결정 F8).
 */
export default function VplcListPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const navigate = useNavigate();
  const [rows, setRows] = useState<Vplc[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: Vplc; error?: string | null }>({ open: false });
  const [connOf, setConnOf] = useState<Vplc | null>(null);
  const [delayOf, setDelayOf] = useState<Vplc | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await vplcApi.list());
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { if (active) void loadRef.current(); }, [active]);

  const save = async (v: Vplc) => {
    try {
      if (form.initial) await vplcApi.update(v); else await vplcApi.register(v);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const act = useCallback((confirm: string | null, run: () => Promise<unknown>) => {
    void (async () => {
      if (confirm && !(await showConfirm({ message: confirm, severity: 'warning' }))) return;
      try {
        await run();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
      await loadRef.current();
    })();
  }, []);

  const control = useCallback((op: VplcControl, v: Vplc) => act(
    op === 'pause' ? t('i18n.tc.vplc.confirmPause', '일시정지하면 연결은 두고 요청에 답하지 않습니다(tc 시간 초과 시험). 멈출까요?') : null,
    () => vplcApi.control(op, v.id),
  ), [act, t]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, r.id, r.description, r.ipAddress, PROTOCOL_LABEL[protocolOf(r)])), [rows, search]);

  /** 종류별 요약 — Modbus 단위 ID·칸·지연, OPC UA 엔드포인트 */
  const summary = useCallback((r: Vplc): string => {
    const s = r.settings ?? {};
    if (protocolOf(r) === 'MODBUS_TCP') {
      return t('i18n.tc.vplc.modbusDesc', '단위 ID {unit} · 영역마다 {size}칸 · 지연 {delay}ms')
        .replace('{unit}', String(s.unitId ?? 1)).replace('{size}', String(s.size ?? 10000)).replace('{delay}', String(s.delayMs ?? 0));
    }
    if (protocolOf(r) === 'OPC_UA') return `opc.tcp://${r.ipAddress}:${r.startPort}${String(s.path ?? '')}`;
    return '';
  }, [t]);

  const columns = useMemo<GridColDef<Vplc>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.point.name', '이름'), width: 170 },
    {
      field: 'protocol', headerName: t('i18n.tc.vplc.kind', '종류'), width: 120, valueGetter: (_v, r) => PROTOCOL_LABEL[protocolOf(r)],
      renderCell: ({ row }) => <Chip size="small" variant="outlined" color={PROTOCOL_COLOR[protocolOf(row)]} label={PROTOCOL_LABEL[protocolOf(row)]} />,
    },
    {
      field: 'status', headerName: t('i18n.tc.schedule.state', '상태'), width: 120,
      renderCell: ({ row }) => <Chip size="small" color={STATUS_COLOR[row.status ?? ''] ?? 'default'} label={row.status ?? '-'} />,
    },
    { field: 'frameFormat', headerName: t('i18n.tc.vplc.frame', '프레임'), width: 90, valueGetter: (v, r) => (protocolOf(r) === 'MELSEC_MC' ? v : '') },
    { field: 'ipAddress', headerName: 'IP', width: 120 },
    {
      field: 'ports', headerName: t('i18n.tc.vplc.ports', '포트'), width: 110, sortable: false,
      valueGetter: (_v, r) => (r.portCount > 1 ? `${r.startPort}~${r.startPort + r.portCount - 1}` : String(r.startPort)),
    },
    { field: 'portConnLimit', headerName: t('i18n.tc.vplc.connLimit', '포트당 연결'), width: 100, type: 'number' },
    {
      field: 'connected', headerName: t('i18n.tc.vplc.connected', '연결 수'), width: 90, type: 'number',
      valueGetter: (_v, r) => (r.connections ?? []).reduce((n, c) => n + (c.connectedCount ?? 0), 0),
      // 누르면 연결 클라이언트 보기(작업 메뉴와 같은 창)
      renderCell: ({ row, value }) => (
        <Link component="button" type="button" underline="hover" aria-label={`clients-${row.name}`}
          title={t('i18n.tc.vplc.viewClients', '연결 클라이언트 보기')}
          onClick={(e) => { e.stopPropagation(); setConnOf(row); }}
          sx={{ width: '100%', textAlign: 'right', fontSize: 'inherit' }}>
          {value as number}
        </Link>
      ),
    },
    {
      field: 'memoryTypeList', headerName: t('i18n.tc.vplc.memoryTypes', '메모리 종류'), width: 150, sortable: false,
      valueGetter: (_v, r) => (protocolOf(r) === 'OPC_UA' ? t('i18n.tc.vplc.tags', '태그') : r.memoryTypeList.map((m) => m.replace('*', '')).join(', ')),
    },
    { field: 'settings', headerName: t('i18n.tc.vplc.settings', '설정'), width: 230, sortable: false, valueGetter: (_v, r) => summary(r) },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 140 },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 140,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [];
        const pub = isPublished(row.status);
        const opc = protocolOf(row) === 'OPC_UA';
        if (pub) {
          acts.push(<GridActionsCellItem key="mem" icon={<MemoryIcon />} label={opc ? t('i18n.tc.vplc.tags', '태그') : t('i18n.tc.vplc.memory', '메모리')}
            onClick={() => navigate(`/vplc/memory?id=${encodeURIComponent(row.id)}`)} />);
        }
        if (perm('X') && pub) {
          if (row.status === 'RUNNING') {
            acts.push(<GridActionsCellItem key="pause" icon={<PauseIcon />} label={t('i18n.tc.vplc.pause', '일시정지')} onClick={() => control('pause', row)} />);
            acts.push(<GridActionsCellItem key="stop" icon={<StopIcon />} label={t('i18n.tc.vplc.stop', '정지')} onClick={() => control('stop', row)} showInMenu />);
          } else if (row.status === 'PAUSED') {
            acts.push(<GridActionsCellItem key="resume" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => control('resume', row)} />);
            acts.push(<GridActionsCellItem key="stop" icon={<StopIcon />} label={t('i18n.tc.vplc.stop', '정지')} onClick={() => control('stop', row)} showInMenu />);
          } else {
            acts.push(<GridActionsCellItem key="start" icon={<PlayArrowIcon />} label={t('i18n.tc.vplc.start', '시작')} onClick={() => control('start', row)} />);
          }
        }
        acts.push(<GridActionsCellItem key="conn" icon={<CableIcon />} label={t('i18n.tc.vplc.viewClients', '연결 클라이언트 보기')} onClick={() => setConnOf(row)} showInMenu />);
        if (perm('X') && (row.status === 'RUNNING' || row.status === 'PAUSED')) {
          acts.push(<GridActionsCellItem key="drop" icon={<LinkOffIcon />} label={t('i18n.tc.vplc.dropConnections', '연결 끊기')} showInMenu
            onClick={() => act(t('i18n.tc.vplc.confirmDropAll', '{name} 에 붙은 연결을 모두 끊을까요? (tc 재연결 시험)').replace('{name}', row.name),
              () => vplcApi.dropConnections(row.id))} />);
        }
        if (perm('U')) {
          if (protocolOf(row) === 'MODBUS_TCP') {
            acts.push(<GridActionsCellItem key="delay" icon={<TimerIcon />} label={t('i18n.tc.vplc.delay', '응답 지연')} onClick={() => setDelayOf(row)} showInMenu />);
          }
          acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} showInMenu />);
        }
        if (perm('X')) {
          acts.push(pub
            ? <GridActionsCellItem key="release" icon={<EventBusyIcon />} label={t('i18n.tc.vplc.release', '해제')} showInMenu
              disabled={row.status === 'RUNNING'} onClick={() => act(null, () => vplcApi.release(row.id))} />
            : <GridActionsCellItem key="publish" icon={<EventAvailableIcon />} label={t('i18n.tc.vplc.publish', '게시')} showInMenu
              onClick={() => act(null, () => vplcApi.publish(row.id))} />);
        }
        if (perm('D')) {
          acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} showInMenu
            onClick={() => act(t('i18n.tc.vplc.confirmDelete', '{name} 을(를) 지울까요?').replace('{name}', row.name), () => vplcApi.remove(row.id))} />);
        }
        return acts;
      },
    },
  ], [t, perm, act, control, navigate, summary]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.vplc.search', '이름·IP·설명')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.id} pinLeft={['name']} isInactive={(r) => !isPublished(r.status)} />
      </Box>
      <VplcFormDialog open={form.open} initial={form.initial} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(v) => { void save(v); }} />
      <VplcConnectionsDialog vplc={connOf} onClose={() => setConnOf(null)} />
      <DelayDialog vplc={delayOf} onClose={() => setDelayOf(null)}
        onSubmit={(ms) => { const v = delayOf; setDelayOf(null); if (v) act(null, () => vplcApi.modbusDelay(v.id, ms)); }} />
    </PageContainer>
  );
}
