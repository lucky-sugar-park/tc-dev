import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Box, Button, Dialog, DialogContent, MenuItem, Stack, TextField } from '@mui/material';
import { DataGridPro, GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showConfirm, showToast, useI18n } from 'wow-mes-frontend-shared';
import { MoreActionsIcon, withActionTooltips } from '../../shared/components/actionTooltips';
import { matches } from '../../shared/format';
import { OPCUA_TYPES, vplcApi } from '../services/vplcApi';
import type { OpcUaSimulation, OpcUaTag, OpcUaTagSpec } from '../types/vplc';

const SIM_KINDS: OpcUaSimulation['kind'][] = ['RAMP', 'SINE', 'TOGGLE', 'RANDOM'];

/** 칸에 쓴 글을 태그 형의 값으로 — 형이 맞지 않으면 null */
export function parseTagValue(type: string, text: string): unknown {
  const s = text.trim();
  if (type === 'STRING') return text;
  if (type === 'BOOL') {
    if (/^(true|1|on)$/i.test(s)) return true;
    if (/^(false|0|off)$/i.test(s)) return false;
    return null;
  }
  const n = Number(s);
  if (!s || !Number.isFinite(n)) return null;
  if (!type.startsWith('FLOAT') && !Number.isInteger(n)) return null;
  return n;
}

const simText = (s?: OpcUaSimulation | null) => (s ? `${s.kind} ${s.periodMs ?? ''}ms${s.min != null ? ` ${s.min}~${s.max}` : ''}` : '');

/** 태그 더하기 — 경로(폴더.이름)·형·처음 값·설명·값 흉내 */
function AddTagDialog({ vplcId, open, onClose, onAdded }: { vplcId: string; open: boolean; onClose: () => void; onAdded: () => void }): ReactElement {
  const { t } = useI18n();
  const [path, setPath] = useState('');
  const [type, setType] = useState('INT16');
  const [value, setValue] = useState('');
  const [description, setDescription] = useState('');
  const [sim, setSim] = useState('');
  const [period, setPeriod] = useState('1000');
  const [min, setMin] = useState('0');
  const [max, setMax] = useState('100');
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (!open) return;
    setPath(''); setType('INT16'); setValue(''); setDescription(''); setSim(''); setPeriod('1000'); setMin('0'); setMax('100'); setError(null);
  }, [open]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!/^[A-Za-z_][A-Za-z0-9_-]*(\.[A-Za-z_][A-Za-z0-9_-]*)*$/.test(path)) {
      setError(t('i18n.tc.vplc.tagPathRule', '영문자·_ 로 시작하는 이름을 . 으로 잇는다(예 Line3.Temp)')); return;
    }
    const spec: OpcUaTagSpec = { path, type, description: description || undefined };
    if (value !== '') {
      const v = parseTagValue(type, value);
      if (v === null) { setError(t('i18n.tc.vplc.tagValueRule', '형에 맞지 않는 값입니다')); return; }
      spec.value = v;
    }
    if (sim) spec.simulation = { kind: sim as OpcUaSimulation['kind'], periodMs: Number(period) || 1000, min: Number(min), max: Number(max) };
    try {
      await vplcApi.addTag(vplcId, spec);
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      onAdded();
    } catch (err) {
      setError(extractApiError(err));
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: (e: FormEvent) => { void submit(e); } } as never }}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.vplc.tagAdd', '태그 추가')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Alert severity="info">{t('i18n.tc.vplc.tagSaved', '태그 정의는 vplc DB 에 남습니다 — 지금 값은 메모리(정지했다 시작하면 처음 값).')}</Alert>
          <Box sx={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.vplc.tagPath', '경로')} required value={path} autoFocus onChange={(e) => setPath(e.target.value)}
              helperText={t('i18n.tc.vplc.tagPathHelp', '앞부분은 폴더 — Line3.Temp')} />
            <TextField select label={t('i18n.tc.point.type', '형')} value={type} onChange={(e) => setType(e.target.value)}>
              {OPCUA_TYPES.map((x) => <MenuItem key={x} value={x}>{x}</MenuItem>)}
            </TextField>
          </Box>
          <TextField label={t('i18n.tc.vplc.initialValue', '처음 값')} value={value} onChange={(e) => setValue(e.target.value)} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={description} onChange={(e) => setDescription(e.target.value)} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1.3fr 1fr 1fr 1fr', gap: 2 }}>
            <TextField select label={t('i18n.tc.vplc.simulation', '값 흉내')} value={sim} onChange={(e) => setSim(e.target.value)}>
              <MenuItem value="">{t('i18n.tc.vplc.off', '끔')}</MenuItem>
              {SIM_KINDS.map((k) => <MenuItem key={k} value={k}>{k}</MenuItem>)}
            </TextField>
            <TextField label={t('i18n.tc.vplc.periodMs', '주기(ms)')} type="number" value={period} disabled={!sim} onChange={(e) => setPeriod(e.target.value)} />
            <TextField label={t('i18n.tc.vplc.min', '최소')} type="number" value={min} disabled={!sim || sim === 'TOGGLE'} onChange={(e) => setMin(e.target.value)} />
            <TextField label={t('i18n.tc.vplc.max', '최대')} type="number" value={max} disabled={!sim || sim === 'TOGGLE'} onChange={(e) => setMax(e.target.value)} />
          </Box>
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

type Row = OpcUaTag & { text: string };

/**
 * 가상 OPC UA 태그 표 — 경로·형·값(실행 중일 때 고쳐 쓰기)·값 흉내·NodeId(nsu=)·설명, 태그 더하기·지우기. 흉내 중인 태그는 다음 주기에 다시 흉내 값이 된다.
 * 새로 고침은 화면이 {@code reloadKey} 를 올려서, 자동 새로 고침은 {@code refreshMs}.
 */
export function OpcUaTagPanel({ vplcId, running, active, search, reloadKey, refreshMs, canWrite, canDelete, addOpen, onAddClose, onError }: {
  vplcId: string;
  /** 실행 중(일시정지 포함)이면 값을 고쳐 쓸 수 있다 */
  running: boolean;
  active: boolean;
  search: string;
  reloadKey: number;
  refreshMs: number;
  canWrite: boolean;
  canDelete: boolean;
  addOpen: boolean;
  onAddClose: () => void;
  onError: (message: string | null) => void;
}): ReactElement {
  const { t } = useI18n();
  const [tags, setTags] = useState<OpcUaTag[]>([]);

  const load = useCallback(async () => {
    try {
      if (!vplcId) { setTags([]); return; }
      setTags(await vplcApi.tags(vplcId));
      onError(null);
    } catch (e) {
      onError(extractApiError(e));
    }
  }, [onError, vplcId]);
  const loadRef = useRef(load);
  loadRef.current = load;
  useEffect(() => { if (active) void loadRef.current(); }, [active, reloadKey, vplcId]);
  useEffect(() => {
    if (!active || !refreshMs) return undefined;
    const h = window.setInterval(() => { void loadRef.current(); }, refreshMs);
    return () => window.clearInterval(h);
  }, [active, refreshMs]);

  const remove = useCallback(async (path: string) => {
    if (!(await showConfirm({ message: t('i18n.tc.vplc.confirmDelete', '{name} 을(를) 지울까요?').replace('{name}', path), severity: 'warning' }))) return;
    try {
      await vplcApi.removeTag(vplcId, path);
      await loadRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, [t, vplcId]);

  const rows = useMemo<Row[]>(() => tags.filter((x) => matches(search, x.path, x.description ?? '', x.type))
    .map((x) => ({ ...x, text: x.value == null ? '' : String(x.value) })), [tags, search]);

  const columns = useMemo<GridColDef<Row>[]>(() => [
    { field: 'path', headerName: t('i18n.tc.vplc.tagPath', '경로'), width: 180 },
    { field: 'type', headerName: t('i18n.tc.point.type', '형'), width: 90 },
    { field: 'text', headerName: t('i18n.tc.vplc.value', '값'), width: 160, editable: canWrite && running },
    { field: 'simulation', headerName: t('i18n.tc.vplc.simulation', '값 흉내'), width: 170, valueGetter: (_v, r) => simText(r.simulation) },
    { field: 'expandedNodeId', headerName: 'NodeId', width: 300 },
    { field: 'description', headerName: t('i18n.tc.common.description', '설명'), flex: 1, minWidth: 140 },
    {
      field: 'actions', type: 'actions', headerName: t('i18n.tc.common.actions', '작업'), width: 60,
      getActions: ({ row }) => (canDelete ? [<GridActionsCellItem key="del" icon={<DeleteOutlineIcon />} label={t('i18n.common.delete', '삭제')}
        onClick={() => { void remove(row.path); }} />] : []),
    },
  ], [t, canWrite, canDelete, remove, running]);

  return (
    <>
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={rows} columns={withActionTooltips(columns)} slots={{ moreActionsIcon: MoreActionsIcon }} getRowId={(r) => r.path} density="compact" hideFooter disableRowSelectionOnClick
            showColumnVerticalBorder showCellVerticalBorder
            processRowUpdate={async (next: Row, prev: Row) => {
              if (next.text === prev.text) return prev;
              const v = parseTagValue(next.type, next.text);
              if (v === null) { showToast(t('i18n.tc.vplc.tagValueRule', '형에 맞지 않는 값입니다'), 'error'); return prev; }
              const saved = await vplcApi.setTagValue(vplcId, next.path, v);
              return { ...saved, text: saved.value == null ? '' : String(saved.value) };
            }}
            onProcessRowUpdateError={(e) => showToast(extractApiError(e), 'error')} />
        </Box>
      </Box>
      <AddTagDialog vplcId={vplcId} open={addOpen} onClose={onAddClose} onAdded={() => { onAddClose(); void loadRef.current(); }} />
    </>
  );
}
