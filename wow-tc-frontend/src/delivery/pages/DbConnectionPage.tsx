import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import NetworkCheckIcon from '@mui/icons-material/NetworkCheck';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showAlert, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { DbConnectionFormDialog } from '../components/DbConnectionFormDialog';
import { dbConnectionApi } from '../services/deliveryApi';
import type { DbConnectionInfo } from '../types/delivery';

const SCREEN = 'tc.delivery.dbconn';

/** 외부 DB 연결 (docs/frontend-design.md §5) — DB_TABLE 전달 대상. 연결 시험, 비밀번호는 저장 여부만 보인다 */
export default function DbConnectionPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<DbConnectionInfo[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: DbConnectionInfo; error?: string | null }>({ open: false });
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await dbConnectionApi.list());
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

  const save = async (c: DbConnectionInfo) => {
    try {
      if (form.initial) await dbConnectionApi.update(form.initial.name, c); else await dbConnectionApi.register(c);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const test = useCallback((c: DbConnectionInfo) => {
    void (async () => {
      try {
        const r = await dbConnectionApi.test(c.name);
        showAlert({
          severity: r.success ? 'success' : 'error',
          title: `${t('i18n.tc.dbconn.test', '연결 시험')} — ${c.name}`,
          message: r.success ? (r.database ?? 'OK') : (r.message ?? 'FAIL'),
        });
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const remove = useCallback((c: DbConnectionInfo) => {
    void (async () => {
      const ok = await showConfirm({
        message: t('i18n.tc.dbconn.confirmDelete', '{name} 을(를) 지울까요? 이 연결을 쓰는 전달 정의가 있으면 지울 수 없습니다.').replace('{name}', c.name),
        severity: 'error',
      });
      if (!ok) return;
      try {
        await dbConnectionApi.remove(c.name);
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t]);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.name, r.jdbcUrl, r.description)), [rows, search]);

  const columns = useMemo<GridColDef<DbConnectionInfo>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.point.name', '이름'), width: 160 },
    { field: 'jdbcUrl', headerName: 'JDBC URL', flex: 1, minWidth: 260 },
    { field: 'username', headerName: t('i18n.tc.dbconn.username', '사용자'), width: 120 },
    {
      field: 'passwordSet', headerName: t('i18n.tc.dbconn.password', '비밀번호'), width: 100,
      renderCell: ({ row }) => <Chip size="small" variant="outlined" color={row.passwordSet ? 'success' : 'warning'}
        label={row.passwordSet ? t('i18n.tc.dbconn.passwordSet', '저장됨') : t('i18n.tc.dbconn.passwordNone', '없음')} />,
    },
    { field: 'maxPoolSize', headerName: t('i18n.tc.dbconn.pool', '최대 연결 수'), width: 100, type: 'number' },
    { field: 'use', headerName: t('i18n.tc.interface.use', '사용'), width: 70, type: 'boolean' },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), width: 180 },
    { field: 'updatedDate', headerName: t('i18n.tc.common.updated', '고친 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 110,
      getActions: ({ row }) => [
        ...(perm('U') ? [<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />] : []),
        ...(perm('X') ? [<GridActionsCellItem key="test" icon={<NetworkCheckIcon />} label={t('i18n.tc.dbconn.test', '연결 시험')} onClick={() => test(row)} />] : []),
        ...(perm('D') ? [<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />] : []),
      ],
    },
  ], [t, perm, test, remove]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.dbconn.search', '이름·URL')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid numbered rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.name} pinLeft={['name']} isInactive={(r) => !r.use} title={t('i18n.tc.dbconn.listTitle', '외부 연결 DB 목록')} />
      </Box>
      <DbConnectionFormDialog open={form.open} initial={form.initial} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(c) => { void save(c); }} />
    </PageContainer>
  );
}
