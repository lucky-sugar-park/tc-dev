import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Box, Button, Checkbox, Dialog, DialogContent, FormControlLabel, Paper, Stack, TextField, Typography } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  DialogFooter, DialogTitleClose, DraggablePaper, LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { matches } from '../../shared/format';
import { usePerm } from '../../shared/usePerm';
import { commonCodeApi } from '../services/adminApi';
import type { CommonCode } from '../types/admin';

const SCREEN = 'tc.admin.code';

function CodeFormDialog({ open, parent, initial, onClose, onSubmit, error }: {
  open: boolean;
  /** 새 코드의 상위(그룹) — 없으면 그룹을 만든다 */
  parent?: string | null;
  initial?: CommonCode;
  onClose: () => void;
  onSubmit: (c: CommonCode) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<CommonCode>({ code: '', name: '', isUse: true });
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!open) return;
    setV(initial ? { ...initial } : { code: parent ? `${parent}.` : 'common.code.', name: '', parent: parent ?? null, isUse: true, i18nCode: '' });
    setErrors({});
  }, [open, initial, parent]);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,255}$/.test(v.code)) errs.code = t('i18n.tc.i18n.codeRule', '영숫자·_ . - 255자까지');
    if (!v.name?.trim()) errs.name = t('i18n.tc.common.required', '필수입니다');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit({ ...v, i18nCode: v.i18nCode?.trim() || undefined });
  };
  const title = edit ? t('i18n.tc.code.edit', '코드 수정') : parent ? t('i18n.tc.code.add', '코드 등록') : t('i18n.tc.code.addGroup', '그룹 등록');
  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{title}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {(v.parent ?? parent) && <TextField label={t('i18n.tc.code.group', '그룹')} value={v.parent ?? parent ?? ''} slotProps={{ input: { readOnly: true } }} />}
          <TextField label={t('i18n.tc.i18n.code', '코드')} required value={v.code} onChange={(e) => setV({ ...v, code: e.target.value })}
            error={!!errors.code} helperText={errors.code} slotProps={{ input: { readOnly: edit } }} />
          <TextField label={t('i18n.tc.user.name', '이름')} required value={v.name ?? ''} onChange={(e) => setV({ ...v, name: e.target.value })}
            error={!!errors.name} helperText={errors.name ?? t('i18n.tc.code.nameHelp', '언어별 이름은 다국어 화면에서 — 다국어 코드를 쓴다')} />
          <TextField label={t('i18n.tc.code.i18nCode', '다국어 코드')} value={v.i18nCode ?? ''} onChange={(e) => setV({ ...v, i18nCode: e.target.value })}
            helperText={t('i18n.tc.code.i18nHelp', '비우면 코드 자체')} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => setV({ ...v, description: e.target.value })} />
          <FormControlLabel control={<Checkbox checked={v.isUse !== false} onChange={(e) => setV({ ...v, isUse: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

/**
 * 공통 코드 (docs/frontend-design.md §5) — 왼쪽 그룹(맨 위 코드), 오른쪽 그 그룹의 코드. 화면 선택지(LOV)가 여기서 온다.
 * 하위 코드가 있는 그룹은 지울 수 없다.
 */
export default function CommonCodePage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [groups, setGroups] = useState<CommonCode[]>([]);
  const [group, setGroup] = useState<string | null>(null);
  const [codes, setCodes] = useState<CommonCode[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; parent?: string | null; initial?: CommonCode; error?: string | null }>({ open: false });
  const { error, fail, clear, retry } = useLoadFailure();
  const groupsRef = useRef(groups);
  groupsRef.current = groups;

  const loadGroups = useCallback(async () => {
    setLoading(true);
    try {
      const g = await commonCodeApi.list();
      setGroups(g);
      setGroup((cur) => (cur && g.some((x) => x.code === cur) ? cur : g[0]?.code ?? null));
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: groupsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear]);
  const loadRef = useRef(loadGroups);
  loadRef.current = loadGroups;
  useEffect(() => { if (active) void loadRef.current(); }, [active]);

  const loadCodes = useCallback(async (g: string | null) => {
    if (!g) { setCodes([]); return; }
    try {
      setCodes(await commonCodeApi.list(g));
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, []);
  useEffect(() => { void loadCodes(group); }, [group, loadCodes]);

  const reload = async () => { await loadGroups(); await loadCodes(group); };

  const save = async (c: CommonCode) => {
    try {
      if (form.initial) await commonCodeApi.update(c); else await commonCodeApi.create(c);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await reload();
    } catch (e) {
      setForm((f) => ({ ...f, error: extractApiError(e) }));
    }
  };

  const remove = useCallback((c: CommonCode) => {
    void (async () => {
      const ok = await showConfirm({ message: t('i18n.tc.code.confirmDelete', '{code} 을(를) 지울까요?').replace('{code}', c.code), severity: 'error' });
      if (!ok) return;
      try {
        await commonCodeApi.remove(c.code);
        if (c.code === group) setGroup(null);
        await loadRef.current();
        await loadCodes(c.code === group ? null : group);
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [t, group, loadCodes]);

  const actions = useCallback((row: CommonCode) => [
    ...(perm('U') ? [<GridActionsCellItem key="edit" icon={<EditIcon />} label={t('i18n.common.edit', '수정')} onClick={() => setForm({ open: true, initial: row })} />] : []),
    ...(perm('D') ? [<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')} onClick={() => remove(row)} showInMenu />] : []),
  ], [t, perm, remove]);

  const groupCols = useMemo<GridColDef<CommonCode>[]>(() => [
    { field: 'name', headerName: t('i18n.tc.code.group', '그룹'), flex: 1, minWidth: 140 },
    { field: 'code', headerName: t('i18n.tc.i18n.code', '코드'), flex: 1, minWidth: 180 },
    { field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 70, getActions: ({ row }) => actions(row) },
  ], [t, actions]);

  const codeCols = useMemo<GridColDef<CommonCode>[]>(() => [
    { field: 'code', headerName: t('i18n.tc.i18n.code', '코드'), width: 300 },
    { field: 'name', headerName: t('i18n.tc.user.name', '이름'), width: 180 },
    { field: 'i18nCode', headerName: t('i18n.tc.code.i18nCode', '다국어 코드'), width: 220 },
    { field: 'isUse', headerName: t('i18n.tc.interface.use', '사용'), width: 70, type: 'boolean' },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 160 },
    { field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 70, getActions: ({ row }) => actions(row) },
  ], [t, actions]);

  const filteredGroups = useMemo(() => groups.filter((g) => matches(search, g.code, g.name)), [groups, search]);

  if (error && groups.length === 0) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <ListToolbar search={search} onSearch={setSearch} onRefresh={() => { void reload(); }}
        onAdd={perm('C') ? () => setForm({ open: true, parent: null }) : undefined} placeholder={t('i18n.tc.code.search', '그룹 코드·이름')} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'grid', gridTemplateColumns: { xs: '1fr', md: '380px 1fr' }, gap: 1 }}>
        <Paper variant="outlined" sx={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <StandardGrid rows={filteredGroups} columns={groupCols} loading={loading} getRowId={(r) => r.code}
            onRowClick={(r) => setGroup(r.code)} selectedId={group ?? undefined} isInactive={(r) => r.isUse === false} />
        </Paper>
        <Paper variant="outlined" sx={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <Stack direction="row" sx={{ alignItems: 'center', px: 1.5, py: 0.5, gap: 1 }}>
            <Typography variant="subtitle2" sx={{ flex: 1 }}>{group ?? t('i18n.tc.code.pickGroup', '왼쪽에서 그룹을 고르세요')}</Typography>
            {group && perm('C') && (
              <Button size="small" startIcon={<AddIcon />} onClick={() => setForm({ open: true, parent: group })}>{t('i18n.tc.code.add', '코드 등록')}</Button>
            )}
          </Stack>
          <StandardGrid rows={codes} columns={codeCols} getRowId={(r) => r.code} isInactive={(r) => r.isUse === false} />
        </Paper>
      </Box>
      <CodeFormDialog open={form.open} parent={form.parent} initial={form.initial} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(c) => { void save(c); }} />
    </PageContainer>
  );
}
