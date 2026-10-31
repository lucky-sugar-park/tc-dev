import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Box, Button, Checkbox, Dialog, DialogContent, FormControlLabel, Stack, TextField } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import type { DbConnectionInfo } from '../types/delivery';

const EMPTY: DbConnectionInfo = { name: '', jdbcUrl: 'jdbc:mariadb://', username: '', password: '', maxPoolSize: 2, use: true, description: '' };

/**
 * 외부 DB 연결 등록·수정 (docs/frontend-design.md §5) — DB_TABLE 전달이 쓴다. 비밀번호는 보내기만 하고 돌려받지 않는다(서버가
 * TC_SECRET_KEY 로 암호화). 수정할 때 비우면 저장된 비밀번호를 그대로 쓴다. 허용 접두어·금지 옵션은 서버가 본다.
 */
export function DbConnectionFormDialog({ open, initial, onClose, onSubmit, error }: {
  open: boolean;
  initial?: DbConnectionInfo;
  onClose: () => void;
  onSubmit: (c: DbConnectionInfo) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<DbConnectionInfo>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => { if (open) { setV(initial ? { ...EMPTY, ...initial, password: '' } : EMPTY); setErrors({}); } }, [open, initial]);
  const set = (patch: Partial<DbConnectionInfo>) => setV((s) => ({ ...s, ...patch }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[a-z0-9_-]{1,100}$/.test(v.name) || v.name === 'tc') errs.name = t('i18n.tc.dbconn.nameRule', '영문 소문자·숫자·_ - (tc 는 쓸 수 없다)');
    if (!v.jdbcUrl.startsWith('jdbc:')) errs.jdbcUrl = t('i18n.tc.dbconn.urlRule', 'jdbc: 로 시작');
    if (!edit && !v.password) errs.password = t('i18n.tc.common.required', '필수입니다');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit({ ...v, password: v.password || undefined, passwordSet: undefined });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.dbconn.edit', 'DB 연결 수정') : t('i18n.tc.dbconn.add', 'DB 연결 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
            error={!!errors.name} helperText={errors.name} slotProps={{ input: { readOnly: edit } }} placeholder="mes-db" />
          <TextField label="JDBC URL" required value={v.jdbcUrl} onChange={(e) => set({ jdbcUrl: e.target.value })}
            error={!!errors.jdbcUrl} helperText={errors.jdbcUrl ?? 'jdbc:mariadb://mes-db:3306/mes'} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.dbconn.username', '사용자')} value={v.username ?? ''} onChange={(e) => set({ username: e.target.value })} autoComplete="off" />
            <TextField label={t('i18n.tc.dbconn.password', '비밀번호')} type="password" value={v.password ?? ''} autoComplete="new-password"
              onChange={(e) => set({ password: e.target.value })} required={!edit} error={!!errors.password}
              helperText={errors.password ?? (edit ? t('i18n.tc.dbconn.passwordKeep', '비우면 그대로') : undefined)} />
            <TextField label={t('i18n.tc.dbconn.pool', '최대 연결 수')} type="number" value={v.maxPoolSize}
              onChange={(e) => set({ maxPoolSize: Math.max(1, Number(e.target.value) || 1) })} slotProps={{ htmlInput: { min: 1, max: 20 } }} />
            <FormControlLabel control={<Checkbox checked={v.use} onChange={(e) => set({ use: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
          </Box>
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
