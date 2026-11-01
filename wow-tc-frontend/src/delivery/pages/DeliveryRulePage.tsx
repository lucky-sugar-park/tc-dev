import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Switch } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { interfaceApi } from '../../plc/services/plcApi';
import { DeliveryRuleFormDialog } from '../components/DeliveryRuleFormDialog';
import { dbConnectionApi, deliveryApi, webhookApi } from '../services/deliveryApi';
import type { DeliveryRule } from '../types/delivery';

const SCREEN = 'tc.delivery.rule';

/**
 * 결과 전달 정의 (docs/frontend-design.md §5) — 인터페이스마다 결과를 어디로(WebHook·DB 테이블·요청의 callback), 언제(늘·성공·실패),
 * 값이 바뀌었을 때만 보낼지. 정의가 없는 인터페이스는 옛 방식(callback + 응답 인터페이스 WebHook)으로 보낸다.
 */
export default function DeliveryRulePage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<DeliveryRule[]>([]);
  const [interfaces, setInterfaces] = useState<string[]>([]);
  const [webhooks, setWebhooks] = useState<string[]>([]);
  const [dbConnections, setDbConnections] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: DeliveryRule; error?: string | null }>({ open: false });
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, ifs, hooks, dbs] = await Promise.all([
        deliveryApi.list(),
        interfaceApi.list().catch(() => []),
        webhookApi.list().catch(() => []),
        dbConnectionApi.list().catch(() => []),
      ]);
      setRows(list);
      setInterfaces(ifs.filter((i) => i.interfaceType === 'READ' || i.interfaceType === 'WRITE').map((i) => i.name).sort());
      setWebhooks(hooks.map((h) => h.name).sort());
      setDbConnections(dbs.map((d) => d.name).sort());
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

  const save = async (rule: DeliveryRule) => {
    try {
      if (form.initial?.id) await deliveryApi.update(form.initial.id, rule); else await deliveryApi.register(rule);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const toggleUse = useCallback(async (r: DeliveryRule, use: boolean) => {
    try {
      await deliveryApi.update(r.id!, { ...r, use });
      setRows((rs) => rs.map((x) => (x.id === r.id ? { ...x, use } : x)));
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, []);

  const remove = useCallback((r: DeliveryRule) => {
    void (async () => {
      const ok = await showConfirm({ message: t('i18n.tc.delivery.confirmDelete', '{id} 을(를) 지울까요?').replace('{id}', r.id ?? ''), severity: 'error' });
      if (!ok) return;
      try {
        await deliveryApi.remove(r.id!);
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.id, r.interfaceName, r.targetName, r.tableName, r.description)), [rows, search]);

  const columns = useMemo<GridColDef<DeliveryRule>[]>(() => [
    { field: 'interfaceName', headerName: t('i18n.tc.delivery.interface', '인터페이스(opcode)'), width: 220 },
    {
      field: 'deliveryType', headerName: t('i18n.tc.delivery.type', '유형'), width: 130,
      renderCell: ({ row }) => <Chip size="small" variant="outlined" label={row.deliveryType} />,
    },
    {
      field: 'target', headerName: t('i18n.tc.delivery.target', '대상'), flex: 1, minWidth: 180,
      valueGetter: (_v, r) => (r.deliveryType === 'CALLBACK_URL' ? t('i18n.tc.delivery.requestCallback', '(요청의 callback)')
        : r.deliveryType === 'DB_TABLE' ? `${r.targetName} / ${r.tableName}` : r.targetName ?? ''),
    },
    {
      field: 'deliverOn', headerName: t('i18n.tc.delivery.deliverOn', '보낼 때'), width: 110,
      valueFormatter: (v: string) => ({ ALL: t('i18n.tc.delivery.on.all', '늘'), SUCCESS: t('i18n.tc.delivery.on.success', '성공일 때만'), FAILURE: t('i18n.tc.delivery.on.failure', '실패일 때만') }[v] ?? v),
    },
    { field: 'onChangeOnly', headerName: t('i18n.tc.delivery.onChange', '바뀔 때만'), width: 90, type: 'boolean' },
    {
      field: 'use', headerName: t('i18n.tc.interface.use', '사용'), width: 80,
      renderCell: ({ row }) => <Switch size="small" checked={row.use} disabled={!perm('U')} onChange={(e) => { void toggleUse(row, e.target.checked); }} />,
    },
    { field: 'id', headerName: t('i18n.tc.delivery.id', '정의 ID'), width: 240 },
    { field: 'updatedDate', headerName: t('i18n.tc.common.updated', '고친 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 80,
      getActions: ({ row }) => [
        ...(perm('U') ? [<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />] : []),
        ...(perm('D') ? [<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />] : []),
      ],
    },
  ], [t, perm, toggleUse, remove]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.delivery.search', '인터페이스·대상·ID')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.id ?? ''} pinLeft={['interfaceName']} isInactive={(r) => !r.use} />
      </Box>
      <DeliveryRuleFormDialog open={form.open} initial={form.initial} interfaces={interfaces} webhooks={webhooks} dbConnections={dbConnections}
        error={form.error} onClose={() => setForm({ open: false })} onSubmit={(r) => { void save(r); }} />
    </PageContainer>
  );
}
