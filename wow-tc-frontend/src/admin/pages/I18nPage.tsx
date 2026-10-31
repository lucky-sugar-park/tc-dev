import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Box, Button, Dialog, DialogContent, MenuItem, Stack, TextField } from '@mui/material';
import { GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  DialogFooter, DialogTitleClose, DraggablePaper, LoadErrorState, PageContainer, extractApiError, showConfirm, showToast, useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { ListToolbar } from '../../shared/components/ListToolbar';
import { StandardGrid } from '../../shared/components/StandardGrid';
import { usePerm } from '../../shared/usePerm';
import { i18nAdminApi } from '../services/adminApi';
import type { I18nEntry } from '../types/admin';

const SCREEN = 'tc.admin.i18n';
const LANGS = ['KO', 'EN', 'JA', 'ZH'] as const;
/** 종류 — 공통 코드 common.code.i18n.kind.*, 이름은 다국어 i18n.<코드>(UiSeedLoader) */
const KINDS = ['label', 'menu', 'message'].map((k, i) => ({
  code: `common.code.i18n.kind.${k}`, i18n: `i18n.common.code.i18n.kind.${k}`, label: ['화면 문구', '메뉴', '메시지'][i],
}));

const EMPTY: I18nEntry = { code: '', kind: KINDS[0].code, values: { KO: '', EN: '', JA: '', ZH: '' }, use: true };

function I18nFormDialog({ open, initial, onClose, onSubmit, error }: {
  open: boolean;
  initial?: I18nEntry;
  onClose: () => void;
  onSubmit: (e: I18nEntry) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<I18nEntry>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!open) return;
    setV(initial ? { ...EMPTY, ...initial, values: { ...EMPTY.values, ...Object.fromEntries(Object.entries(initial.values).map(([k, x]) => [k, x ?? ''])) } } : EMPTY);
    setErrors({});
  }, [open, initial]);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,255}$/.test(v.code)) errs.code = t('i18n.tc.i18n.codeRule', '영숫자·_ . - 255자까지');
    if (!v.values.KO?.trim()) errs.KO = t('i18n.tc.common.required', '필수입니다');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit(v);
  };
  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.i18n.edit', '다국어 수정') : t('i18n.tc.i18n.add', '다국어 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.i18n.code', '코드')} required value={v.code} onChange={(e) => setV({ ...v, code: e.target.value })}
              error={!!errors.code} helperText={errors.code ?? t('i18n.tc.i18n.codeHelp', '예: i18n.tc.connector.add')} slotProps={{ input: { readOnly: edit } }} />
            <TextField select label={t('i18n.tc.audit.type', '종류')} value={v.kind ?? KINDS[0].code} onChange={(e) => setV({ ...v, kind: e.target.value })}>
              {KINDS.map((k) => <MenuItem key={k.code} value={k.code}>{t(k.i18n, k.label)}</MenuItem>)}
            </TextField>
          </Box>
          {LANGS.map((l) => (
            <TextField key={l} label={l} required={l === 'KO'} multiline maxRows={4} value={v.values[l] ?? ''}
              onChange={(e) => setV({ ...v, values: { ...v.values, [l]: e.target.value } })}
              error={!!errors[l]} helperText={errors[l] ?? (l === 'KO' ? undefined : t('i18n.tc.i18n.emptyHelp', '비우면 그 언어는 지운다(한국어로 보인다)'))} />
          ))}
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => setV({ ...v, description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

/**
 * 다국어 (docs/frontend-design.md §5) — 코드별 KO·EN·JA·ZH. 메뉴 이름·화면 문구를 재기동 없이 고친다(화면은 다시 읽을 때 반영).
 * 씨앗(ui-seed/i18n)은 없는 코드만 넣으므로 여기서 고친 값은 덮이지 않는다.
 */
export default function I18nPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [rows, setRows] = useState<I18nEntry[]>([]);
  const [search, setSearch] = useState('');
  const [kind, setKind] = useState('');
  const [missing, setMissing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [form, setForm] = useState<{ open: boolean; initial?: I18nEntry; error?: string | null }>({ open: false });
  const { error, fail, clear, retry } = useLoadFailure();
  const rowsRef = useRef(rows);
  rowsRef.current = rows;

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await i18nAdminApi.list(undefined, kind));
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: rowsRef.current.length > 0, retry: () => { void loadRef.current(); } });
    } finally {
      setLoading(false);
    }
  }, [kind, fail, clear]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { if (active) void loadRef.current(); }, [active, kind]);

  const save = async (e: I18nEntry) => {
    try {
      await i18nAdminApi.save(e);
      setForm({ open: false });
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await loadRef.current();
    } catch (err) {
      setForm((f) => ({ ...f, error: extractApiError(err) }));
    }
  };

  const remove = useCallback((e: I18nEntry) => {
    void (async () => {
      const ok = await showConfirm({ message: t('i18n.tc.i18n.confirmDelete', '{code} 을(를) 네 언어 모두 지울까요?').replace('{code}', e.code), severity: 'error' });
      if (!ok) return;
      try {
        await i18nAdminApi.remove(e.code);
        await loadRef.current();
      } catch (err) {
        showToast(extractApiError(err), 'error');
      }
    })();
  }, [t]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => (!q || r.code.toLowerCase().includes(q) || Object.values(r.values).some((x) => x?.toLowerCase().includes(q)))
      && (!missing || LANGS.some((l) => !r.values[l])));
  }, [rows, search, missing]);

  const columns = useMemo<GridColDef<I18nEntry>[]>(() => [
    { field: 'code', headerName: t('i18n.tc.i18n.code', '코드'), width: 300 },
    ...LANGS.map((l): GridColDef<I18nEntry> => ({
      field: l, headerName: l, flex: 1, minWidth: 160, valueGetter: (_v, r) => r.values[l] ?? '',
      cellClassName: (p) => (p.value ? '' : 'cell-missing'),
    })),
    {
      field: 'actions', type: 'actions', headerName: '', width: 80,
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
        onAdd={perm('U') ? () => setForm({ open: true }) : undefined} placeholder={t('i18n.tc.i18n.search', '코드·값')}
        filters={(
          <>
            <TextField select size="small" label={t('i18n.tc.audit.type', '종류')} value={kind} onChange={(e) => setKind(e.target.value)} sx={{ width: 150 }}
              slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true }, htmlInput: { 'aria-label': t('i18n.tc.audit.type', '종류') } }}>
              <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
              {KINDS.map((k) => <MenuItem key={k.code} value={k.code}>{t(k.i18n, k.label)}</MenuItem>)}
            </TextField>
            <TextField select size="small" label={t('i18n.tc.i18n.missing', '빠진 언어')} value={missing ? '1' : ''} onChange={(e) => setMissing(e.target.value === '1')} sx={{ width: 150 }}
              slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true }, htmlInput: { 'aria-label': t('i18n.tc.i18n.missing', '빠진 언어') } }}>
              <MenuItem value="">{t('i18n.tc.common.all', '전체')}</MenuItem>
              <MenuItem value="1">{t('i18n.tc.i18n.missingOnly', '빠진 것만')}</MenuItem>
            </TextField>
          </>
        )} />
      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', '& .cell-missing': { bgcolor: 'warning.light', opacity: 0.6 } }}>
        <StandardGrid rows={filtered} columns={columns} loading={loading} getRowId={(r) => r.code} pinLeft={['code']} />
      </Box>
      <I18nFormDialog open={form.open} initial={form.initial} error={form.error}
        onClose={() => setForm({ open: false })} onSubmit={(e) => { void save(e); }} />
    </PageContainer>
  );
}
