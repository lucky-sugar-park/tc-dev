import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import KeyIcon from '@mui/icons-material/Key';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { interfaceApi } from '../../plc/services/plcApi';
import { ApiClientFormDialog, ApiKeysDialog } from '../components/ApiClientDialogs';
import { apiClientApi } from '../services/adminApi';
import type { ApiClient } from '../types/admin';

const SCREEN = 'tc.admin.apiclient';

/** 만료가 이만큼 남으면 눈에 띄게 */
const SOON_MS = 30 * 86400_000;

/**
 * API 클라이언트 (docs/frontend-design.md §5) — 다른 시스템이 API 키로 tc 를 부른다. 목록(활성 키 수·가까운 만료·마지막 사용), 등록·수정
 * (허용 opcode·IP·URL 접두어·한도), 사용 중지·재개, 키 발급·폐기, 삭제.
 */
export default function ApiClientPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<ApiClient[]>([]);
  const [opcodes, setOpcodes] = useState<string[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: ApiClient; error?: string | null }>({ open: false });
  const [keysOf, setKeysOf] = useState<string | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [clients, ifs] = await Promise.all([apiClientApi.list(), interfaceApi.list().catch(() => [])]);
      setRows(clients);
      setOpcodes(ifs.map((i) => i.name).sort());
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

  const save = async (c: ApiClient) => {
    try {
      if (form.initial) await apiClientApi.update(c);
      else await apiClientApi.register(c);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
      if (!form.initial) setKeysOf(c.clientId); // 새 클라이언트는 바로 키를 발급하게
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const act = useCallback((confirm: string | null, run: () => Promise<unknown>) => {
    void (async () => {
      if (confirm && !(await showConfirm({ message: confirm, severity: 'warning' }))) return;
      try {
        await run();
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, []);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.clientId, r.name, r.description, ...r.allowedOpcodes)), [rows, search]);

  const columns = useMemo<GridColDef<ApiClient>[]>(() => [
    { field: 'clientId', headerName: t('i18n.tc.apiclient.clientId', '클라이언트 ID'), width: 170 },
    { field: 'name', headerName: t('i18n.tc.user.name', '이름'), width: 160 },
    { field: 'enabled', headerName: t('i18n.tc.interface.use', '사용'), width: 70, type: 'boolean' },
    {
      field: 'activeKeyCount', headerName: t('i18n.tc.apiclient.activeKeys', '활성 키'), width: 90, type: 'number',
      renderCell: ({ row }) => <Chip size="small" color={row.activeKeyCount ? 'success' : 'warning'} label={row.activeKeyCount ?? 0} />,
    },
    {
      field: 'nearestExpiresAt', headerName: t('i18n.tc.apiclient.nearestExpiry', '가까운 만료'), width: 170,
      renderCell: ({ row }) => {
        if (!row.nearestExpiresAt) return '';
        const soon = new Date(row.nearestExpiresAt).getTime() - Date.now() < SOON_MS;
        return <Box component="span" sx={{ color: soon ? 'error.main' : undefined, fontWeight: soon ? 600 : undefined }}>{fmtTime(row.nearestExpiresAt)}</Box>;
      },
    },
    { field: 'lastUsedAt', headerName: t('i18n.tc.apiclient.lastUsed', '마지막 사용'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'allowedOpcodes', headerName: t('i18n.tc.apiclient.opcodes', '허용 opcode'), flex: 1, minWidth: 220, sortable: false,
      valueGetter: (_v, r) => r.allowedOpcodes.join(', '),
    },
    { field: 'allowedIps', headerName: t('i18n.tc.apiclient.ips', '허용 IP'), width: 160, sortable: false, valueGetter: (_v, r) => r.allowedIps.join(', ') },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 110,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [
          <GridActionsCellItem key="keys" icon={<KeyIcon />} label={t('i18n.tc.apiclient.keys', 'API 키')} onClick={() => setKeysOf(row.clientId)} />,
        ];
        if (perm('U')) {
          acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />);
          acts.push(row.enabled
            ? <GridActionsCellItem key="disable" icon={<BlockIcon />} label={t('i18n.tc.user.disable', '사용 중지')} showInMenu
              onClick={() => act(t('i18n.tc.apiclient.confirmDisable', '{id} 의 사용을 멈출까요? 이 클라이언트의 호출은 바로 거절됩니다.').replace('{id}', row.clientId),
                () => apiClientApi.setEnabled(row.clientId, false))} />
            : <GridActionsCellItem key="enable" icon={<CheckCircleOutlineIcon />} label={t('i18n.tc.user.enable', '사용 재개')} showInMenu
              onClick={() => act(null, () => apiClientApi.setEnabled(row.clientId, true))} />);
        }
        if (perm('D')) {
          acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} showInMenu
            onClick={() => act(t('i18n.tc.apiclient.confirmDelete', '{id} 을(를) 지울까요? 키도 모두 폐기되고, 이 ID 는 다시 쓸 수 없습니다.').replace('{id}', row.clientId),
              () => apiClientApi.remove(row.clientId))} />);
        }
        return acts;
      },
    },
  ], [t, perm, act]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.apiclient.search', 'ID·이름·opcode')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.clientId} pinLeft={['clientId']} title={t('i18n.tc.apiclient.listTitle', 'API 클라이언트 목록')} isInactive={(r) => !r.enabled} />
      </Box>
      <ApiClientFormDialog open={form.open} initial={form.initial} opcodes={opcodes} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(c) => { void save(c); }} />
      <ApiKeysDialog clientId={keysOf} canManage={perm('U')} onClose={() => setKeysOf(null)} onChanged={() => { void loadRef.current(); }} />
    </PageContainer>
  );
}
