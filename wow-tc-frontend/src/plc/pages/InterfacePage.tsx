import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, MenuItem, Switch, TextField } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityIcon from '@mui/icons-material/Visibility';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import HistoryIcon from '@mui/icons-material/History';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  FilterField, LoadErrorState, PageContainer, downloadExcel, excelErrorMessage, extractApiError, fileStamp, showConfirm, showToast, useI18n,
  useLoadFailure,
} from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { useCan, usePerm } from '../../shared/usePerm';
import { InterfaceAuditDialog } from '../components/InterfaceAuditDialog';
import { InterfaceDetailDialog } from '../components/InterfaceDetailDialog';
import { InterfaceFormDialog } from '../components/InterfaceFormDialog';
import { RawInterfaceDialog } from '../components/RawInterfaceDialog';
import { connectorApi, fetchProtocols, fetchWebhookNames, interfaceApi } from '../services/plcApi';
import type { InterfaceInfo, PlcConnectorInfo, ProtocolDescriptor } from '../types/plc';

const SCREEN = 'tc.plc.interface';

/**
 * 인터페이스 (docs/frontend-design.md §5) — 목록(형·PLC·정의 방식 필터), POINTS 등록·수정(점 표), RAW 보기 + POINTS 변환, 사용 켜고 끄기, 삭제.
 * 응답(REPLY) 인터페이스는 RAW 의 부속이라 기본으로 숨긴다.
 */
export default function InterfacePage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  // 송수신 이력은 송수신 기록 화면을 볼 수 있는 사람만(서버도 같은 권한으로 막는다)
  const canAudit = useCan('uiroute.tc.ops.audit', 'R');
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<InterfaceInfo[]>([]);
  const [connectors, setConnectors] = useState<PlcConnectorInfo[]>([]);
  const [protocols, setProtocols] = useState<ProtocolDescriptor[]>([]);
  const [webhooks, setWebhooks] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'READ' | 'WRITE' | 'REPLY'>('ALL');
  const [modeFilter, setModeFilter] = useState<'ALL' | 'POINTS' | 'RAW'>('ALL');
  const [plcFilter, setPlcFilter] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: InterfaceInfo; error?: string | null }>({ open: false });
  const [rawOf, setRawOf] = useState<string | null>(null);
  const [auditOf, setAuditOf] = useState<string | null>(null);
  const [detailOf, setDetailOf] = useState<string | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, cons, protos, hooks] = await Promise.all([
        interfaceApi.list(), connectorApi.list(), fetchProtocols(), fetchWebhookNames().catch(() => [] as string[]),
      ]);
      setRows(list);
      setConnectors(cons);
      setProtocols(protos);
      setWebhooks(hooks);
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

  const openEdit = useCallback(async (r: InterfaceInfo) => {
    try {
      setForm({ open: true, initial: await interfaceApi.find(r.name) });
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, []);

  const save = async (info: InterfaceInfo) => {
    try {
      if (form.initial) await interfaceApi.update(info); else await interfaceApi.register(info);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const toggleUse = useCallback(async (r: InterfaceInfo, use: boolean) => {
    try {
      await interfaceApi.setUse(r.id!, use);
      setRows((rs) => rs.map((x) => (x.name === r.name ? { ...x, use } : x)));
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, []);

  const remove = useCallback((r: InterfaceInfo) => {
    void (async () => {
      const ok = await showConfirm({
        message: t('i18n.tc.interface.confirmDelete', '{name} 을(를) 지울까요? 이 opcode 로 오는 요청이 실패합니다.').replace('{name}', r.name),
        severity: 'error',
      });
      if (!ok) return;
      try {
        await interfaceApi.remove(r.name);
        showToast(`${r.name} ${t('i18n.common.delete', '삭제')}`, 'success');
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const filtered = useMemo(() => rows.filter((r) => {
    if (typeFilter === 'ALL' ? r.interfaceType === 'REPLY' : r.interfaceType !== typeFilter) return false;
    if (modeFilter !== 'ALL' && (r.mode ?? 'RAW') !== modeFilter) return false;
    if (plcFilter && r.plcName !== plcFilter) return false;
    return matches(search, r.name, r.description, r.plcName);
  }), [rows, typeFilter, modeFilter, plcFilter, search]);

  const columns = useMemo<GridColDef<InterfaceInfo>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.interface.name', '이름(opcode)'), width: 240 },
    { field: 'interfaceType', headerName: t('i18n.tc.interface.ifType', '타입'), width: 80 },
    { field: 'plcName', headerName: t('i18n.tc.interface.plc', 'PLC 커넥터'), width: 140 },
    {
      field: 'mode', headerName: t('i18n.tc.interface.mode', '정의'), width: 90,
      renderCell: ({ row }) => <Chip size="small" variant="outlined" color={row.mode === 'POINTS' ? 'primary' : 'default'} label={row.mode ?? 'RAW'} />,
    },
    // 응답 인터페이스는 RAW 에만 있다 — POINTS 는 읽기 하나 = 인터페이스 하나(점은 수정 대화상자에서)
    { field: 'replyName', headerName: t('i18n.tc.interface.replyIf', '응답 IF'), width: 200, valueGetter: (_v, r) => r.replyName ?? '' },
    {
      field: 'use', headerName: t('i18n.tc.interface.use', '사용'), width: 80,
      renderCell: ({ row }) => (
        <Switch size="small" checked={!!row.use} disabled={!perm('U') || row.id == null}
          onChange={(e) => { void toggleUse(row, e.target.checked); }} slotProps={{ input: { 'aria-label': `use-${row.name}` } }} />
      ),
    },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 160 },
    { field: 'updatedDate', headerName: t('i18n.tc.common.updated', '고친 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 90,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [];
        if (row.mode === 'POINTS') {
          acts.push(<GridActionsCellItem key="detail" icon={<InfoOutlinedIcon />} label={t('i18n.tc.interface.detail', '상세 보기')}
            onClick={() => setDetailOf(row.name)} showInMenu />);
          if (perm('U')) acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => { void openEdit(row); }} />);
        } else {
          acts.push(<GridActionsCellItem key="raw" icon={<VisibilityIcon />} label={t('i18n.tc.raw.view', 'RAW 보기·변환')} onClick={() => setRawOf(row.name)} />);
        }
        if (canAudit) {
          acts.push(<GridActionsCellItem key="audit" icon={<HistoryIcon />} label={t('i18n.tc.interface.auditHistory', '송수신 이력 보기')}
            onClick={() => setAuditOf(row.name)} showInMenu />);
        }
        if (perm('D')) acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />);
        return acts;
      },
    },
  ], [t, perm, canAudit, openEdit, toggleUse, remove]);

  const exportExcel = () => {
    downloadExcel({
      fileName: `interfaces_${fileStamp()}`,
      sheets: [{
        name: t('i18n.menu.tc.plc.interface', '인터페이스'),
        rows: filtered.map((r) => ({
          opcode: r.name, [t('i18n.tc.interface.ifType', '타입')]: r.interfaceType, [t('i18n.tc.interface.plc', 'PLC 커넥터')]: r.plcName ?? '',
          [t('i18n.tc.interface.mode', '정의')]: r.mode ?? 'RAW', [t('i18n.tc.interface.use', '사용')]: r.use ? 'Y' : 'N',
          [t('i18n.tc.interface.points', '점')]: (r.points ?? []).map((p) => `${p.name}=${p.address}:${p.type}`).join(' '),
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
        placeholder={t('i18n.tc.interface.search', '이름·설명')}
        filters={(
          <>
            <FilterField label={t('i18n.tc.interface.ifType', '타입')}>
              <TextField select size="small" value={typeFilter} sx={{ width: 140 }}
                onChange={(e) => setTypeFilter(e.target.value as typeof typeFilter)}
                slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.interface.ifType', '타입') } }}>
                <MenuItem value="ALL">READ·WRITE</MenuItem>
                <MenuItem value="READ">READ</MenuItem>
                <MenuItem value="WRITE">WRITE</MenuItem>
                <MenuItem value="REPLY">REPLY</MenuItem>
              </TextField>
            </FilterField>
            <FilterField label={t('i18n.tc.interface.mode', '정의')}>
              <TextField select size="small" value={modeFilter} sx={{ width: 120 }}
                onChange={(e) => setModeFilter(e.target.value as typeof modeFilter)}
                slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.interface.mode', '정의') } }}>
                <MenuItem value="ALL">{t('i18n.tc.common.all', '전체')}</MenuItem>
                <MenuItem value="POINTS">POINTS</MenuItem>
                <MenuItem value="RAW">RAW</MenuItem>
              </TextField>
            </FilterField>
            <FilterField label={t('i18n.tc.interface.plc', 'PLC 커넥터')}>
              <TextField select size="small" value={plcFilter} sx={{ width: 170 }}
                onChange={(e) => setPlcFilter(e.target.value)}
                slotProps={{ htmlInput: { 'aria-label': t('i18n.tc.interface.plc', 'PLC 커넥터') }, select: { displayEmpty: true } }}>
                <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
                {connectors.map((c) => <MenuItem key={c.name} value={c.name}>{c.name}</MenuItem>)}
              </TextField>
            </FilterField>
          </>
        )} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']} isInactive={(r) => !r.use} />
      </Box>
      <InterfaceFormDialog open={form.open} initial={form.initial} connectors={connectors} protocols={protocols} webhooks={webhooks}
        error={form.error} onClose={() => setForm({ open: false })} onSubmit={(info) => { void save(info); }} />
      <RawInterfaceDialog name={rawOf} canConvert={perm('U')} onClose={() => setRawOf(null)}
        onConverted={() => { setRawOf(null); showToast(t('i18n.tc.convert.done', 'POINTS 로 바꿨습니다'), 'success'); void loadRef.current(); }} />
      <InterfaceAuditDialog name={auditOf} onClose={() => setAuditOf(null)} />
      <InterfaceDetailDialog name={detailOf} connectors={connectors} onClose={() => setDetailOf(null)} />
    </PageContainer>
  );
}
