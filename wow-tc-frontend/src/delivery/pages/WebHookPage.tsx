import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { WebHookFormDialog } from '../components/WebHookFormDialog';
import { webhookApi } from '../services/deliveryApi';
import type { WebHookInfo } from '../types/delivery';

const SCREEN = 'tc.delivery.webhook';

/** WebHook (docs/frontend-design.md §5) — 결과를 POST 로 받을 주소. 전달 정의(WEBHOOK)와 인터페이스의 WebHook 목록이 이름으로 쓴다 */
export default function WebHookPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<WebHookInfo[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: WebHookInfo; error?: string | null }>({ open: false });
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await webhookApi.list());
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

  const save = async (h: WebHookInfo) => {
    try {
      if (form.initial) await webhookApi.update(h); else await webhookApi.register(h);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const remove = useCallback((h: WebHookInfo) => {
    void (async () => {
      const ok = await showConfirm({
        message: t('i18n.tc.webhook.confirmDelete', '{name} 을(를) 지울까요? 이 WebHook 을 쓰는 전달 정의는 보내지 못합니다.').replace('{name}', h.name),
        severity: 'error',
      });
      if (!ok) return;
      try {
        await webhookApi.remove(h.name);
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, r.url, r.description)), [rows, search]);

  const columns = useMemo<GridColDef<WebHookInfo>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.point.name', '이름'), width: 200 },
    { field: 'url', headerName: 'URL', flex: 1, minWidth: 280 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 180 },
    { field: 'updatedDate', headerName: t('i18n.tc.common.updated', '고친 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 80,
      getActions: ({ row }) => [
        ...(perm('U') ? [<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />] : []),
        ...(perm('D') ? [<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />] : []),
      ],
    },
  ], [t, perm, remove]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.webhook.search', '이름·URL')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']} title={t('i18n.tc.webhook.listTitle', 'WebHook 목록')} />
      </Box>
      <WebHookFormDialog open={form.open} initial={form.initial} canTest={perm('X')} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(h) => { void save(h); }} />
    </PageContainer>
  );
}
