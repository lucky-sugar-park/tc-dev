import { useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogContent, FormControlLabel, FormGroup, FormLabel, Stack, TextField,
} from '@mui/material';
import { DataGridPro, type GridColDef } from '@mui/x-data-grid-pro';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showToast, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { userApi } from '../services/adminApi';
import type { TcUser, UserHistory, UserRole } from '../types/admin';

/** 새로 줄 수 있는 역할 — 나머지(MANAGER·EMPLOYEE·CLIENT)는 예전 값이라 보이기만 한다 */
export const ROLES: { role: UserRole; label: string; code: string }[] = [
  { role: 'ROLE_ADMIN', label: '관리자', code: 'i18n.tc.user.role.admin' },
  { role: 'ROLE_OPERATOR', label: '운영자', code: 'i18n.tc.user.role.operator' },
  { role: 'ROLE_VIEWER', label: '조회자', code: 'i18n.tc.user.role.viewer' },
];

const MIN_PASSWORD = 10;

export type UserForm = { id: string; name?: string; email?: string; userRoles: UserRole[]; initialPassword: string };

/** 사용자 등록·수정 — 아이디·이름·이메일·역할, 등록 때는 초기 비밀번호(첫 로그인에서 바꾼다) */
export function UserFormDialog({ open, initial, onClose, onSubmit, error }: {
  open: boolean;
  initial?: TcUser;
  onClose: () => void;
  onSubmit: (u: UserForm) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState({ id: '', name: '', email: '', userRoles: ['ROLE_VIEWER'] as UserRole[], initialPassword: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!open) return;
    setV(initial
      ? { id: initial.id, name: initial.name ?? '', email: initial.email ?? '', userRoles: [...initial.userRoles], initialPassword: '' }
      : { id: '', name: '', email: '', userRoles: ['ROLE_VIEWER'], initialPassword: '' });
    setErrors({});
  }, [open, initial]);

  const legacy = v.userRoles.filter((r) => !ROLES.some((x) => x.role === r));
  const toggle = (r: UserRole, on: boolean) => setV((s) => ({ ...s, userRoles: on ? [...s.userRoles, r] : s.userRoles.filter((x) => x !== r) }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.@-]{2,100}$/.test(v.id)) errs.id = t('i18n.tc.user.idRule', '영숫자·_ . @ - 2~100자');
    if (v.userRoles.length === 0) errs.roles = t('i18n.tc.user.roleRequired', '역할을 하나 이상 고르세요');
    if (v.email && !/^\S+@\S+\.\S+$/.test(v.email)) errs.email = t('i18n.tc.user.emailRule', '이메일 형식이 아닙니다');
    if (!edit && (v.initialPassword.length < MIN_PASSWORD || v.initialPassword === v.id)) {
      errs.initialPassword = t('i18n.tc.user.passwordRule', '10자 이상, 아이디와 달라야 합니다');
    }
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit(v);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.user.edit', '사용자 수정') : t('i18n.tc.user.add', '사용자 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label={t('i18n.auth.id', '아이디')} required value={v.id} onChange={(e) => setV({ ...v, id: e.target.value })}
            error={!!errors.id} helperText={errors.id} slotProps={{ input: { readOnly: edit } }} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.user.name', '이름')} value={v.name} onChange={(e) => setV({ ...v, name: e.target.value })} />
            <TextField label={t('i18n.tc.user.email', '이메일')} value={v.email} onChange={(e) => setV({ ...v, email: e.target.value })}
              error={!!errors.email} helperText={errors.email} />
          </Box>
          <Box>
            <FormLabel>{t('i18n.tc.user.roles', '역할')}</FormLabel>
            <FormGroup row>
              {ROLES.map((r) => (
                <FormControlLabel key={r.role} label={t(r.code, r.label)}
                  control={<Checkbox checked={v.userRoles.includes(r.role)} onChange={(e) => toggle(r.role, e.target.checked)} />} />
              ))}
            </FormGroup>
            {legacy.length > 0 && (
              <Alert severity="warning" sx={{ mt: 1 }}>
                {t('i18n.tc.user.legacyRoles', '예전 역할이 남아 있습니다 — 저장하면 그대로 둡니다. 빼려면 지우세요.')}
                <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                  {legacy.map((r) => <Chip key={r} size="small" label={r} onDelete={() => toggle(r, false)} />)}
                </Stack>
              </Alert>
            )}
            {errors.roles && <Alert severity="error" sx={{ mt: 1 }}>{errors.roles}</Alert>}
          </Box>
          {!edit && (
            <TextField label={t('i18n.tc.user.initialPassword', '초기 비밀번호')} type="password" required autoComplete="new-password"
              value={v.initialPassword} onChange={(e) => setV({ ...v, initialPassword: e.target.value })}
              error={!!errors.initialPassword}
              helperText={errors.initialPassword ?? t('i18n.tc.user.initialHelp', '첫 로그인에서 바꾸게 한다 — 10자 이상')} />
          )}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

/** 비밀번호 초기화 — 그 사용자의 토큰을 모두 지우고, 다음 로그인에서 바꾸게 한다 */
export function PasswordResetDialog({ user, onClose }: { user: TcUser | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [pw, setPw] = useState('');
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { setPw(''); setError(null); }, [user]);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!user) return;
    if (pw.length < MIN_PASSWORD || pw === user.id) { setError(t('i18n.tc.user.passwordRule', '10자 이상, 아이디와 달라야 합니다')); return; }
    try {
      await userApi.resetPassword(user.id, pw);
      showToast(t('i18n.tc.user.resetDone', '비밀번호를 초기화했습니다 — 다음 로그인에서 바꿉니다'), 'success');
      onClose();
    } catch (err) {
      setError(extractApiError(err));
    }
  };
  return (
    <Dialog open={!!user} onClose={onClose} maxWidth="xs" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: (e: FormEvent) => { void submit(e); } } as never }}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.user.resetPassword', '비밀번호 초기화')} — {user?.id}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Alert severity="info">{t('i18n.tc.user.resetHelp', '이 사용자의 로그인을 모두 끊고, 다음 로그인에서 비밀번호를 바꾸게 합니다.')}</Alert>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label={t('i18n.tc.user.newPassword', '새 비밀번호')} type="password" autoComplete="new-password" required value={pw}
            onChange={(e) => setPw(e.target.value)} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}>
        <Button type="submit" variant="contained" color="warning">{t('i18n.tc.user.resetPassword', '비밀번호 초기화')}</Button>
      </DialogFooter>
    </Dialog>
  );
}

const OPER_COLOR: Record<string, 'success' | 'error' | 'warning' | 'default' | 'info'> = {
  LOGIN: 'success', LOGIN_FAIL: 'error', LOGIN_THROTTLED: 'error', LOGIN_UNBLOCK: 'warning', LOGOUT: 'default',
  PASSWORD_CHANGE: 'info', REISSUE_DENIED: 'warning',
};

/** 사용 이력 — 로그인·실패·막힘·로그아웃·비밀번호 변경 … 최신순 */
export function UserHistoryDialog({ userId, onClose }: { userId: string | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [rows, setRows] = useState<UserHistory[]>([]);
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    setRows([]);
    if (!userId) return;
    setLoading(true);
    userApi.history(userId).then(setRows).catch((e) => showToast(extractApiError(e), 'error')).finally(() => setLoading(false));
  }, [userId]);
  const cols = useMemo<GridColDef<UserHistory>[]>(() => [
    { field: 'timestamp', headerName: t('i18n.tc.audit.time', '시각'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'oper', headerName: t('i18n.tc.user.oper', '일'), width: 170,
      renderCell: ({ row }) => <Chip size="small" variant="outlined" color={OPER_COLOR[row.oper] ?? 'default'} label={row.oper} />,
    },
    { field: 'ipAddress', headerName: 'IP', width: 140 },
    { field: 'remark', headerName: t('i18n.tc.schedule.remark', '비고'), flex: 1, minWidth: 200 },
  ], [t]);
  return (
    <Dialog open={!!userId} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.user.history', '사용 이력')} — {userId}</DialogTitleClose>
      <DialogContent dividers sx={{ height: '60vh', display: 'flex' }}>
        <Box sx={{ flex: 1, position: 'relative' }}>
          <Box sx={{ position: 'absolute', inset: 0 }}>
            <DataGridPro rows={rows} columns={cols} getRowId={(r) => r.seqNo} loading={loading} density="compact" disableRowSelectionOnClick hideFooter />
          </Box>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
