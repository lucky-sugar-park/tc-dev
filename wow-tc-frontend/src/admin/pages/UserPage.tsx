import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement } from 'react';
import { Box, Chip, Stack } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import BlockIcon from '@mui/icons-material/Block';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import LockResetIcon from '@mui/icons-material/LockReset';
import LockOpenIcon from '@mui/icons-material/LockOpen';
import HistoryIcon from '@mui/icons-material/History';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import { LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure } from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { fmtTime, matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { PasswordResetDialog, ROLES, UserFormDialog, UserHistoryDialog, type UserForm } from '../components/UserDialogs';
import { userApi } from '../services/adminApi';
import type { TcUser } from '../types/admin';

const SCREEN = 'tc.admin.user';

/**
 * 사용자 (docs/frontend-design.md §5) — 목록(역할·사용·로그인 막힘·마지막 로그인), 등록·수정, 사용 중지·재개, 비밀번호 초기화,
 * 로그인 제한 풀기, 사용 이력, 삭제. 계정 잠금은 없다 — 실패가 많으면 잠시 막힐 뿐이고(auth-and-audit-design §3.4) 관리자가 바로 풀 수 있다.
 */
export default function UserPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<TcUser[]>([]);
  const [blocked, setBlocked] = useState<Set<string>>(new Set());
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: TcUser; error?: string | null }>({ open: false });
  const [resetOf, setResetOf] = useState<TcUser | null>(null);
  const [historyOf, setHistoryOf] = useState<string | null>(null);
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [users, b] = await Promise.all([userApi.list(), userApi.loginBlocked().catch(() => [] as string[])]);
      setRows(users);
      setBlocked(new Set(b));
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

  const save = async (u: UserForm) => {
    try {
      if (form.initial) await userApi.update(u.id, { name: u.name, email: u.email, userRoles: u.userRoles });
      else await userApi.create(u);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  /** (확인을 받고) 하고 나서 목록을 다시 읽는다 */
  const act = useCallback((confirm: string | null, run: () => Promise<unknown>, done?: string) => {
    void (async () => {
      if (confirm && !(await showConfirm({ message: confirm, severity: 'warning' }))) return;
      try {
        await run();
        if (done) showToast(done, 'success');
        await loadRef.current();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, []);

  const filtered = useMemo(() => rows.filter((r) => matches(search, r.id, r.name, r.email)), [rows, search]);

  const columns = useMemo<GridColDef<TcUser>[]>(() => [
    { field: 'id', headerName: t('i18n.auth.id', '아이디'), width: 150 },
    { field: 'name', headerName: t('i18n.tc.user.name', '이름'), width: 140 },
    { field: 'email', headerName: t('i18n.tc.user.email', '이메일'), width: 200 },
    {
      field: 'userRoles', headerName: t('i18n.tc.user.roles', '역할'), width: 200, sortable: false,
      valueGetter: (_v, r) => r.userRoles.join(','),
      renderCell: ({ row }) => (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
          {row.userRoles.map((r) => {
            const known = ROLES.find((x) => x.role === r);
            return <Chip key={r} size="small" variant="outlined" color={r === 'ROLE_ADMIN' ? 'primary' : 'default'} label={known ? t(known.code, known.label) : r} />;
          })}
        </Stack>
      ),
    },
    {
      field: 'state', headerName: t('i18n.tc.schedule.state', '상태'), width: 200, sortable: false,
      valueGetter: (_v, r) => [r.enabled ? '' : 'disabled', blocked.has(r.id) ? 'blocked' : '', r.passwordChangeRequired ? 'mustChange' : ''].join(' '),
      renderCell: ({ row }) => (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
          {!row.enabled && <Chip size="small" label={t('i18n.tc.user.disabled', '사용 중지')} />}
          {blocked.has(row.id) && <Chip size="small" color="error" label={t('i18n.tc.user.blocked', '로그인 막힘')} />}
          {row.passwordChangeRequired && <Chip size="small" color="warning" variant="outlined" label={t('i18n.tc.user.mustChange', '비밀번호 바꿀 차례')} />}
        </Stack>
      ),
    },
    { field: 'lastLoginAt', headerName: t('i18n.tc.user.lastLogin', '마지막 로그인'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'lastLoginIp', headerName: 'IP', width: 130 },
    { field: 'passwordChangedAt', headerName: t('i18n.tc.user.passwordChanged', '비밀번호 바꾼 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 110,
      getActions: ({ row }) => {
        const acts: ReactElement[] = [];
        if (perm('U')) {
          acts.push(<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />);
          if (blocked.has(row.id)) {
            acts.push(<GridActionsCellItem key="unblock" icon={<LockOpenIcon />} label={t('i18n.tc.user.unblock', '로그인 제한 풀기')}
              onClick={() => act(null, () => userApi.unblock(row.id), t('i18n.tc.user.unblocked', '로그인 제한을 풀었습니다'))} />);
          }
          acts.push(row.enabled
            ? <GridActionsCellItem key="disable" icon={<BlockIcon />} label={t('i18n.tc.user.disable', '사용 중지')} showInMenu
              onClick={() => act(t('i18n.tc.user.confirmDisable', '{id} 의 사용을 멈출까요? 로그인이 바로 끊깁니다.').replace('{id}', row.id),
                () => userApi.setEnabled(row.id, false))} />
            : <GridActionsCellItem key="enable" icon={<CheckCircleOutlineIcon />} label={t('i18n.tc.user.enable', '사용 재개')} showInMenu
              onClick={() => act(null, () => userApi.setEnabled(row.id, true))} />);
          acts.push(<GridActionsCellItem key="reset" icon={<LockResetIcon />} label={t('i18n.tc.user.resetPassword', '비밀번호 초기화')} showInMenu
            onClick={() => setResetOf(row)} />);
        }
        acts.push(<GridActionsCellItem key="hist" icon={<HistoryIcon />} label={t('i18n.tc.user.history', '사용 이력')} showInMenu
          onClick={() => setHistoryOf(row.id)} />);
        if (perm('D')) {
          acts.push(<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} showInMenu
            onClick={() => act(t('i18n.tc.user.confirmDelete', '{id} 을(를) 지울까요? 운영에서는 사용 중지를 권합니다.').replace('{id}', row.id),
              () => userApi.remove(row.id))} />);
        }
        return acts;
      },
    },
  ], [t, perm, blocked, act]);

  if (error && rows.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void load(); }}
        onAdd={perm('C') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.user.search', '아이디·이름·이메일')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.id} pinLeft={['id']} isInactive={(r) => !r.enabled} />
      </Box>
      <UserFormDialog open={form.open} initial={form.initial} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(u) => { void save(u); }} />
      <PasswordResetDialog user={resetOf} onClose={() => setResetOf(null)} />
      <UserHistoryDialog userId={historyOf} onClose={() => setHistoryOf(null)} />
    </PageContainer>
  );
}
