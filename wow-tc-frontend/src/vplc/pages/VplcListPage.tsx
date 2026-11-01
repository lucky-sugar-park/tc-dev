import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Alert, Box, Chip, Stack } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import { useNavigate } from 'react-router-dom';
import EditIcon from '@mui/icons-material/Edit';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import StopIcon from '@mui/icons-material/Stop';
import PauseIcon from '@mui/icons-material/Pause';
import MemoryIcon from '@mui/icons-material/Memory';
import CableIcon from '@mui/icons-material/Cable';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import EventAvailableIcon from '@mui/icons-material/EventAvailable';
import EventBusyIcon from '@mui/icons-material/EventBusy';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import AddToQueueIcon from '@mui/icons-material/AddToQueue';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, ScreenAction, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { ModbusDeviceDialog, VplcConnectionsDialog, VplcFormDialog, type ModbusDeviceForm } from '../components/VplcDialogs';
import { KIND_LABEL, OPCUA_NAME, STATUS_COLOR, isPublished, modbusApi, opcUaApi, vplcApi, type VplcControl } from '../services/vplcApi';
import type { ModbusDevice, OpcUaInfo, Vplc, VplcKind } from '../types/vplc';

const SCREEN = 'tc.vplc.list';

/** 목록 한 줄 — 세 장비(vplc·vplc-modbus·vplc-opcua)를 한 표로 */
type Row = {
  key: string;
  kind: VplcKind;
  name: string;
  status: string;
  frame?: string;
  ipAddress?: string;
  ports: string;
  portConnLimit?: number;
  connected: number;
  memoryTypes: string;
  description?: string;
  mc?: Vplc;
  modbus?: ModbusDevice;
  opcua?: OpcUaInfo;
};

const KIND_COLOR: Record<VplcKind, 'default' | 'primary' | 'secondary'> = { MC: 'default', MODBUS: 'primary', OPCUA: 'secondary' };

const mcRow = (v: Vplc): Row => ({
  key: `MC:${v.id}`, kind: 'MC', name: v.name, status: v.status ?? '-', frame: v.frameFormat, ipAddress: v.ipAddress,
  ports: v.portCount > 1 ? `${v.startPort}~${v.startPort + v.portCount - 1}` : String(v.startPort), portConnLimit: v.portConnLimit,
  connected: (v.connections ?? []).reduce((n, c) => n + (c.connectedCount ?? 0), 0),
  memoryTypes: v.memoryTypeList.map((m) => m.replace('*', '')).join(', '), description: v.description, mc: v,
});

const modbusRow = (d: ModbusDevice, t: (k: string, f: string) => string): Row => ({
  key: `MODBUS:${d.name}`, kind: 'MODBUS', name: d.name, status: d.silent ? 'PAUSED' : 'RUNNING', ipAddress: d.bindAddress, ports: String(d.port),
  connected: d.connections, memoryTypes: 'HR, IR, CO, DI',
  description: t('i18n.tc.vplc.modbusDesc', '단위 ID {unit} · 영역마다 {size}칸 · 지연 {delay}ms')
    .replace('{unit}', String(d.unitId)).replace('{size}', String(d.size)).replace('{delay}', String(d.delayMs)),
  modbus: d,
});

const opcUaRow = (i: OpcUaInfo, t: (k: string, f: string) => string): Row => {
  const m = /^opc\.tcp:\/\/([^:/]+):(\d+)/.exec(i.endpointUrl);
  return {
    key: 'OPCUA:', kind: 'OPCUA', name: OPCUA_NAME, status: 'RUNNING', ipAddress: m?.[1], ports: m?.[2] ?? '-', connected: i.sessions,
    memoryTypes: t('i18n.tc.vplc.tagCount', '태그 {n}').replace('{n}', String(i.tags)), description: `${i.endpointUrl} · ns=${i.namespaceIndex}`, opcua: i,
  };
};

/**
 * 가상 PLC 목록 (docs/frontend-design.md §5, docs/virtual-devices.md) — tc 를 시험하는 가상 장비. MELSEC(vplc): 등록·수정, 게시·해제,
 * 시작·정지·일시정지(무응답)·재개, 포트별 연결, 삭제. Modbus(vplc-modbus): 장치 더하기·지우기, 일시정지(무응답)·재개, 응답 지연, 연결 끊기.
 * OPC UA(vplc-opcua): 서버 하나(태그는 메모리 화면에서). 화면이 장비를 바로 부른다(결정 F8) — 떠 있지 않은 장비는 알림만 띄우고 나머지를 보인다.
 */
export default function VplcListPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const navigate = useNavigate();
  const [rows, setRows] = useState<Row[]>([]);
  const [down, setDown] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: Vplc; error?: string | null }>({ open: false });
  const [mbForm, setMbForm] = useState<{ open: boolean; initial?: ModbusDevice; error?: string | null }>({ open: false });
  const [connOf, setConnOf] = useState<Vplc | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const sources: { kind: VplcKind; get: () => Promise<Row[]> }[] = [];
      if (vplcApi.enabled()) sources.push({ kind: 'MC', get: async () => (await vplcApi.list()).map(mcRow) });
      if (modbusApi.enabled()) sources.push({ kind: 'MODBUS', get: async () => (await modbusApi.list()).map((d) => modbusRow(d, t)) });
      if (opcUaApi.enabled()) sources.push({ kind: 'OPCUA', get: async () => [opcUaRow(await opcUaApi.info(), t)] });
      const results = await Promise.allSettled(sources.map((s) => s.get()));
      const failed = results.flatMap((r, i) => (r.status === 'rejected' ? [`${KIND_LABEL[sources[i].kind]}: ${extractApiError(r.reason)}`] : []));
      if (failed.length === sources.length && failed.length) throw new Error(failed.join(' / '));
      setRows(results.flatMap((r) => (r.status === 'fulfilled' ? r.value : [])));
      setDown(failed);
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear, t]);
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

  const saveModbus = async (d: ModbusDeviceForm) => {
    try {
      if (mbForm.initial) await modbusApi.behavior(d.name, { delayMs: d.delayMs }); else await modbusApi.add(d);
      setMbForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setMbForm((f) => ({ ...f, error: extractApiError(e) }));
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

  const pauseConfirm = t('i18n.tc.vplc.confirmPause', '일시정지하면 연결은 두고 요청에 답하지 않습니다(tc 시간 초과 시험). 멈출까요?');
  const control = useCallback((op: VplcControl, v: Vplc) => act(op === 'pause' ? pauseConfirm : null, () => vplcApi.control(op, v.id)), [act, pauseConfirm]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, KIND_LABEL[r.kind], r.description, r.ipAddress)), [rows, search]);

  const mcActions = useCallback((row: Row, v: Vplc): ReactElement[] => {
    const acts: ReactElement[] = [];
    const pub = isPublished(v.status);
    if (pub) {
      acts.push(<GridActionsCellItem key="mem" icon={<MemoryIcon />} label={t('i18n.tc.vplc.memory', '메모리')}
        onClick={() => navigate(`/vplc/memory?id=${encodeURIComponent(v.id)}`)} />);
    }
    if (perm('X') && pub) {
      if (v.status === 'RUNNING') {
        acts.push(<GridActionsCellItem key="pause" icon={<PauseIcon />} label={t('i18n.tc.vplc.pause', '일시정지')} onClick={() => control('pause', v)} />);
        acts.push(<GridActionsCellItem key="stop" icon={<StopIcon />} label={t('i18n.tc.vplc.stop', '정지')} onClick={() => control('stop', v)} showInMenu />);
      } else if (v.status === 'PAUSED') {
        acts.push(<GridActionsCellItem key="resume" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => control('resume', v)} />);
        acts.push(<GridActionsCellItem key="stop" icon={<StopIcon />} label={t('i18n.tc.vplc.stop', '정지')} onClick={() => control('stop', v)} showInMenu />);
      } else {
        acts.push(<GridActionsCellItem key="start" icon={<PlayArrowIcon />} label={t('i18n.tc.vplc.start', '시작')} onClick={() => control('start', v)} />);
      }
    }
    acts.push(<GridActionsCellItem key="conn" icon={<CableIcon />} label={t('i18n.tc.vplc.connections', '연결')} onClick={() => setConnOf(v)} showInMenu />);
    if (perm('U')) {
      acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: v })} showInMenu />);
    }
    if (perm('X')) {
      acts.push(pub
        ? <GridActionsCellItem key="release" icon={<EventBusyIcon />} label={t('i18n.tc.vplc.release', '해제')} showInMenu
          disabled={v.status === 'RUNNING'} onClick={() => act(null, () => vplcApi.release(v.id))} />
        : <GridActionsCellItem key="publish" icon={<EventAvailableIcon />} label={t('i18n.tc.vplc.publish', '게시')} showInMenu
          onClick={() => act(null, () => vplcApi.publish(v.id))} />);
    }
    if (perm('D')) {
      acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} showInMenu
        onClick={() => act(t('i18n.tc.vplc.confirmDelete', '{name} 을(를) 지울까요?').replace('{name}', row.name), () => vplcApi.remove(v.id))} />);
    }
    return acts;
  }, [t, perm, act, control, navigate]);

  const modbusActions = useCallback((d: ModbusDevice): ReactElement[] => {
    const acts: ReactElement[] = [
      <GridActionsCellItem key="mem" icon={<MemoryIcon />} label={t('i18n.tc.vplc.memory', '메모리')}
        onClick={() => navigate(`/vplc/memory?kind=modbus&id=${encodeURIComponent(d.name)}`)} />,
    ];
    if (perm('X')) {
      acts.push(d.silent
        ? <GridActionsCellItem key="resume" icon={<PlayArrowIcon />} label={t('i18n.tc.connector.resume', '재개')}
          onClick={() => act(null, () => modbusApi.behavior(d.name, { silent: false }))} />
        : <GridActionsCellItem key="pause" icon={<PauseIcon />} label={t('i18n.tc.vplc.pause', '일시정지')}
          onClick={() => act(pauseConfirm, () => modbusApi.behavior(d.name, { silent: true }))} />);
      acts.push(<GridActionsCellItem key="drop" icon={<LinkOffIcon />} label={t('i18n.tc.vplc.dropConnections', '연결 끊기')} showInMenu
        onClick={() => act(t('i18n.tc.vplc.confirmDrop', '포트 {port} 의 연결을 모두 끊을까요? (tc 재연결 시험)').replace('{port}', String(d.port)),
          () => modbusApi.dropConnections(d.name))} />);
    }
    if (perm('U')) {
      acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.tc.vplc.delay', '응답 지연')} showInMenu
        onClick={() => setMbForm({ open: true, initial: d })} />);
    }
    if (perm('D')) {
      acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} showInMenu
        onClick={() => act(t('i18n.tc.vplc.confirmDelete', '{name} 을(를) 지울까요?').replace('{name}', d.name), () => modbusApi.remove(d.name))} />);
    }
    return acts;
  }, [t, perm, act, navigate, pauseConfirm]);

  const columns = useMemo<GridColDef<Row>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.point.name', '이름'), width: 170 },
    {
      field: 'kind', headerName: t('i18n.tc.vplc.kind', '종류'), width: 120, valueGetter: (_v, r) => KIND_LABEL[r.kind],
      renderCell: ({ row }) => <Chip size="small" variant="outlined" color={KIND_COLOR[row.kind]} label={KIND_LABEL[row.kind]} />,
    },
    {
      field: 'status', headerName: t('i18n.tc.schedule.state', '상태'), width: 120,
      renderCell: ({ row }) => <Chip size="small" color={STATUS_COLOR[row.status] ?? 'default'} label={row.status} />,
    },
    { field: 'frame', headerName: t('i18n.tc.vplc.frame', '프레임'), width: 90 },
    { field: 'ipAddress', headerName: 'IP', width: 120 },
    { field: 'ports', headerName: t('i18n.tc.vplc.ports', '포트'), width: 110, sortable: false },
    { field: 'portConnLimit', headerName: t('i18n.tc.vplc.connLimit', '포트당 연결'), width: 100, type: 'number' },
    { field: 'connected', headerName: t('i18n.tc.vplc.connected', '연결 수'), width: 90, type: 'number' },
    { field: 'memoryTypes', headerName: t('i18n.tc.vplc.memoryTypes', '메모리 종류'), width: 160, sortable: false },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 160 },
    {
      field: 'actions', type: 'actions', headerName: '', width: 140,
      getActions: ({ row }) => {
        if (row.mc) return mcActions(row, row.mc);
        if (row.modbus) return modbusActions(row.modbus);
        return [<GridActionsCellItem key="mem" icon={<MemoryIcon />} label={t('i18n.tc.vplc.tags', '태그')}
          onClick={() => navigate('/vplc/memory?kind=opcua')} />];
      },
    },
  ], [t, mcActions, modbusActions, navigate]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') && vplcApi.enabled() ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.vplc.search', '이름·IP·설명')}
        actions={perm('C') && modbusApi.enabled() && (
          <ScreenAction icon={<AddToQueueIcon fontSize="small" />} label={t('i18n.tc.vplc.modbusAdd', 'Modbus 장치 추가')} onClick={() => setMbForm({ open: true })} />
        )} />
      {down.length > 0 && (
        <Stack spacing={0.5}>
          {down.map((d) => <Alert key={d} severity="warning">{t('i18n.tc.vplc.deviceDown', '가상 장비에 붙지 못했습니다(떠 있는지 확인)')} — {d}</Alert>)}
        </Stack>
      )}
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.key} pinLeft={['name']}
          isInactive={(r) => r.kind === 'MC' && !isPublished(r.status)} />
      </Box>
      <VplcFormDialog open={form.open} initial={form.initial} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(v) => { void save(v); }} />
      <ModbusDeviceDialog open={mbForm.open} initial={mbForm.initial} error={mbForm.error}
        onClose={() => setMbForm({ open: false })} onSubmit={(d) => { void saveModbus(d); }} />
      <VplcConnectionsDialog vplc={connOf} onClose={() => setConnOf(null)} />
    </PageContainer>
  );
}
