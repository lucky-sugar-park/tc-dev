import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogContent, IconButton, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography,
} from '@mui/material';
import { DataGridPro, type GridColDef } from '@mui/x-data-grid-pro';
import { useSearchParams } from 'react-router-dom';
import NavigateBeforeIcon from '@mui/icons-material/NavigateBefore';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import StopIcon from '@mui/icons-material/Stop';
import EditIcon from '@mui/icons-material/Edit';
import DeleteSweepIcon from '@mui/icons-material/DeleteSweep';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  CommandBarActions, DialogFooter, DialogTitleClose, DraggablePaper, FilterBar, PageContainer, ScreenAction, extractApiError, showConfirm, showToast,
  useI18n,
} from 'wow-mes-frontend-shared';
import { usePerm } from '../../shared/usePerm';
import { MEMORY_TYPES, STATUS_COLOR, addressLabel, isPublished, vplcApi, type VplcControl } from '../services/vplcApi';
import type { Vplc, VplcMemory } from '../types/vplc';

const SCREEN = 'tc.vplc.memory';
const PAGE_SIZES = [20, 50, 100];
const REFRESH = [0, 1000, 2000, 5000];

type Row = { addr: number; word: number; text: string };

const hex4 = (w: number) => (w & 0xFFFF).toString(16).toUpperCase().padStart(4, '0');
const printable = (s?: string) => (s ?? '').replace(/[^\x20-\x7E]/g, '·');

/** 값 쓰기 — 워드 값 여러 개(쉼표·공백, 10진·0x16진) 또는 문자열(ASCII, 한 워드에 두 글자) */
function WriteDialog({ open, memoryType, start, onClose, onWrite }: {
  open: boolean;
  memoryType: string;
  start: number;
  onClose: () => void;
  onWrite: (start: number, input: { words?: number[]; text?: string }) => Promise<void>;
}): ReactElement {
  const { t } = useI18n();
  const [mode, setMode] = useState<'words' | 'text'>('words');
  const [addr, setAddr] = useState(String(start));
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (open) { setMode('words'); setAddr(String(start)); setValue(''); setError(null); } }, [open, start]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const a = Number(addr);
    if (!Number.isInteger(a) || a < 0) { setError(t('i18n.tc.vplc.addrRule', '0 이상의 정수 주소')); return; }
    if (mode === 'words') {
      const parts = value.split(/[\s,]+/).filter(Boolean);
      const words = parts.map((p) => (/^0x/i.test(p) ? parseInt(p, 16) : Number(p)));
      if (!parts.length || words.some((w) => !Number.isInteger(w) || w < -32768 || w > 65535)) {
        setError(t('i18n.tc.vplc.wordRule', '-32768~65535 정수(0x 로 시작하면 16진)를 쉼표로')); return;
      }
      await onWrite(a, { words });
    } else {
      if (!value || /[^\x20-\x7E]/.test(value)) { setError(t('i18n.tc.vplc.textRule', 'ASCII 글자만')); return; }
      await onWrite(a, { text: value });
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: (e: FormEvent) => { void submit(e); } } as never }}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.vplc.write', '값 쓰기')} — {memoryType.replace('*', '')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <ToggleButtonGroup exclusive size="small" value={mode} onChange={(_e, m) => m && setMode(m)}>
            <ToggleButton value="words">{t('i18n.tc.vplc.modeWords', '워드 값')}</ToggleButton>
            <ToggleButton value="text">{t('i18n.tc.vplc.modeText', '문자열(ASCII)')}</ToggleButton>
          </ToggleButtonGroup>
          <TextField label={t('i18n.tc.vplc.startAddress', '시작 주소')} type="number" value={addr} onChange={(e) => setAddr(e.target.value)}
            helperText={addressLabel(memoryType, Number(addr) || 0)} />
          <TextField label={mode === 'words' ? t('i18n.tc.vplc.words', '값(쉼표로 여러 개)') : t('i18n.tc.vplc.text', '문자열')} value={value} autoFocus
            onChange={(e) => setValue(e.target.value)}
            helperText={mode === 'words' ? t('i18n.tc.vplc.wordsHelp', '예: 1234, -1, 0x00FF — 차례로 이어진 주소에 쓴다')
              : t('i18n.tc.vplc.textHelp', '한 워드에 두 글자 — 낮은 바이트가 앞 글자(MC 와 같다)')} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.tc.vplc.write', '값 쓰기')}</Button></DialogFooter>
    </Dialog>
  );
}

/**
 * 가상 PLC 메모리 보기·제어 (docs/frontend-design.md §5) — 메모리 종류·시작 주소·쪽 크기로 표(주소·10진·16진·비트·ASCII), 자동 새로 고침,
 * 10진 칸을 고쳐 쓰기·비트를 눌러 켜고 끄기·여러 값/문자열 쓰기, 종류 전체 지우기, 시작·일시정지(무응답)·재개·정지.
 */
export default function VplcMemoryPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const canWrite = perm('U');
  const canControl = perm('X');
  const active = useIsActiveScreen();
  // 목록에서 ?id= 로 들어오면 그 PLC — 고른 PLC 를 주소에 다시 쓰지 않는다(주소가 바뀌면 탭이 하나 더 열린다)
  const [params] = useSearchParams();
  const [vplcs, setVplcs] = useState<Vplc[]>([]);
  const [vplcId, setVplcId] = useState(params.get('id') ?? '');
  const [memType, setMemType] = useState('');
  const [start, setStart] = useState(0);
  const [startInput, setStartInput] = useState('0');
  const [size, setSize] = useState(20);
  const [refresh, setRefresh] = useState(0);
  const [mem, setMem] = useState<VplcMemory | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [writeOpen, setWriteOpen] = useState(false);
  const vplc = vplcs.find((v) => v.id === vplcId) ?? null;

  const loadVplcs = useCallback(async () => {
    try {
      const list = (await vplcApi.list()).filter((v) => isPublished(v.status));
      setVplcs(list);
      setVplcId((cur) => (cur && list.some((v) => v.id === cur) ? cur : list[0]?.id ?? ''));
    } catch (e) {
      setError(extractApiError(e));
    }
  }, []);
  useEffect(() => { if (active) void loadVplcs(); }, [active, loadVplcs]);

  // 가상 PLC 를 바꾸면 그 PLC 의 첫 메모리 종류로
  useEffect(() => {
    if (!vplc) return;
    setMemType((cur) => (vplc.memoryTypeList.includes(cur) ? cur : vplc.memoryTypeList[0] ?? ''));
  }, [vplc]);

  const read = useCallback(async () => {
    if (!vplc || !memType) { setMem(null); return; }
    try {
      setMem(await vplcApi.read(vplc, memType, start, size));
      setError(null);
    } catch (e) {
      setError(extractApiError(e));
    }
  }, [vplc, memType, start, size]);
  const readRef = useRef(read);
  readRef.current = read;
  useEffect(() => { if (active) void read(); }, [active, read]);
  useEffect(() => {
    if (!active || !refresh) return undefined;
    const h = window.setInterval(() => { void readRef.current(); }, refresh);
    return () => window.clearInterval(h);
  }, [active, refresh]);

  const max = MEMORY_TYPES.find((m) => m.code === memType)?.max ?? 0xFFFFF;
  const bitDevice = !!MEMORY_TYPES.find((m) => m.code === memType)?.bit;
  const go = (s: number) => { const n = Math.max(0, Math.min(s, max)); setStart(n); setStartInput(String(n)); };

  const writeWords = useCallback(async (addr: number, words: number[]) => {
    if (!vplc) return;
    try {
      await vplcApi.writeWords(vplc, memType, addr, words);
      await readRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, [vplc, memType]);

  const write = async (addr: number, input: { words?: number[]; text?: string }) => {
    if (!vplc) return;
    try {
      if (input.words) await vplcApi.writeWords(vplc, memType, addr, input.words);
      else if (input.text) await vplcApi.writeText(vplc, memType, addr, input.text);
      setWriteOpen(false);
      showToast(t('i18n.tc.vplc.written', '썼습니다'), 'success');
      await readRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  const control = async (op: VplcControl) => {
    if (!vplc) return;
    if (op === 'pause' && !(await showConfirm({ message: t('i18n.tc.vplc.confirmPause', '일시정지하면 연결은 두고 요청에 답하지 않습니다(tc 시간 초과 시험). 멈출까요?'), severity: 'warning' }))) return;
    try {
      await vplcApi.control(op, vplc.id);
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
    await loadVplcs();
  };

  const clearAll = async () => {
    if (!vplc) return;
    const ok = await showConfirm({
      message: t('i18n.tc.vplc.confirmClear', '{type} 메모리를 모두 0 으로 지울까요?').replace('{type}', memType.replace('*', '')), severity: 'error',
    });
    if (!ok) return;
    try {
      await vplcApi.clearAll(vplc, memType, start);
      await readRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  const rows = useMemo<Row[]>(() => (mem?.wordInfos ?? []).map((w, i) => ({ addr: start + i, word: w.word, text: printable(w.lhStr) })), [mem, start]);

  const columns = useMemo<GridColDef<Row>[]>(() => [
    { field: 'addr', headerName: t('i18n.tc.vplc.address', '주소'), width: 110, valueFormatter: (v: number) => addressLabel(memType, v) },
    // 메모리 값은 천 단위 쉼표 없이
    { field: 'word', headerName: t('i18n.tc.vplc.decimal', '10진'), width: 110, type: 'number', editable: canWrite, valueFormatter: (v: number) => String(v) },
    { field: 'unsigned', headerName: t('i18n.tc.vplc.unsigned', '부호 없음'), width: 100, type: 'number', valueGetter: (_v, r) => r.word & 0xFFFF, valueFormatter: (v: number) => String(v) },
    { field: 'hex', headerName: '16진', width: 80, valueGetter: (_v, r) => hex4(r.word) },
    {
      field: 'bits', headerName: t('i18n.tc.vplc.bits', '비트(15 → 0)'), width: 360, sortable: false,
      renderCell: ({ row }) => (
        <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center', height: '100%' }}>
          {Array.from({ length: 16 }, (_, k) => 15 - k).map((b) => {
            const on = ((row.word >> b) & 1) === 1;
            const label = bitDevice ? addressLabel(memType, row.addr * 16 + b) : `b${b}`;
            return (
              <Tooltip key={b} title={label}>
                <Box component="button" type="button" aria-label={`${label} ${on ? 1 : 0}`} disabled={!canWrite}
                  onClick={(e) => { e.stopPropagation(); void writeWords(row.addr, [(row.word & 0xFFFF) ^ (1 << b)]); }}
                  sx={{
                    width: 18, height: 18, p: 0, border: 1, borderColor: 'divider', borderRadius: 0.5, fontSize: 10, lineHeight: '16px',
                    cursor: canWrite ? 'pointer' : 'default', bgcolor: on ? 'success.main' : 'transparent', color: on ? 'success.contrastText' : 'text.secondary',
                    ml: b === 7 ? 0.75 : 0,
                  }}>
                  {on ? 1 : 0}
                </Box>
              </Tooltip>
            );
          })}
        </Stack>
      ),
    },
    { field: 'text', headerName: 'ASCII', width: 80, sortable: false },
  ], [t, memType, canWrite, bitDevice, writeWords]);

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void loadVplcs(); void read(); }} />
        {canControl && vplc && (vplc.status === 'RUNNING'
          ? (
            <>
              <ScreenAction icon={<PauseIcon fontSize="small" />} label={t('i18n.tc.vplc.pause', '일시정지')} onClick={() => { void control('pause'); }} />
              <ScreenAction icon={<StopIcon fontSize="small" />} label={t('i18n.tc.vplc.stop', '정지')} onClick={() => { void control('stop'); }} />
            </>
          )
          : vplc.status === 'PAUSED'
            ? <ScreenAction icon={<PlayArrowIcon fontSize="small" />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => { void control('resume'); }} />
            : <ScreenAction icon={<PlayArrowIcon fontSize="small" />} label={t('i18n.tc.vplc.start', '시작')} onClick={() => { void control('start'); }} />)}
        {canWrite && vplc && (
          <>
            <ScreenAction icon={<EditIcon fontSize="small" />} label={t('i18n.tc.vplc.write', '값 쓰기')} onClick={() => setWriteOpen(true)} />
            <ScreenAction icon={<DeleteSweepIcon fontSize="small" />} label={t('i18n.tc.vplc.clearAll', '모두 지우기')} onClick={() => { void clearAll(); }} />
          </>
        )}
      </CommandBarActions>
      <FilterBar onSearch={() => go(Number(startInput) || 0)} onReset={() => { go(0); setSize(20); }}>
        <TextField select size="small" label={t('i18n.tc.vplc.title', '가상 PLC')} value={vplc ? vplcId : ''} sx={{ width: 200 }}
          onChange={(e) => setVplcId(e.target.value)}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.title', '가상 PLC') } }}>
          {vplcs.map((v) => <MenuItem key={v.id} value={v.id}>{v.name}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('i18n.tc.vplc.memoryType', '메모리')} value={memType} sx={{ width: 120 }}
          onChange={(e) => { setMemType(e.target.value); go(0); }}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.memoryType', '메모리') } }}>
          {(vplc?.memoryTypeList ?? []).map((m) => <MenuItem key={m} value={m}>{m.replace('*', '')}</MenuItem>)}
        </TextField>
        <TextField size="small" type="number" label={t('i18n.tc.vplc.startAddress', '시작 주소')} value={startInput} sx={{ width: 130 }}
          onChange={(e) => setStartInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') go(Number(startInput) || 0); }}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.startAddress', '시작 주소'), min: 0 } }} />
        <TextField select size="small" label={t('i18n.tc.vplc.pageSize', '워드 수')} value={size} sx={{ width: 100 }} onChange={(e) => setSize(Number(e.target.value))}
          slotProps={{ inputLabel: { shrink: true } }}>
          {PAGE_SIZES.map((n) => <MenuItem key={n} value={n}>{n}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('i18n.tc.vplc.autoRefresh', '자동 새로 고침')} value={refresh} sx={{ width: 140 }}
          onChange={(e) => setRefresh(Number(e.target.value))}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.autoRefresh', '자동 새로 고침') } }}>
          {REFRESH.map((n) => <MenuItem key={n} value={n}>{n ? `${n / 1000}s` : t('i18n.tc.vplc.off', '끔')}</MenuItem>)}
        </TextField>
      </FilterBar>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', px: 0.5 }}>
        {vplc && <Chip size="small" color={STATUS_COLOR[vplc.status ?? ''] ?? 'default'} label={vplc.status} />}
        {vplc && (
          <Typography variant="caption" color="text.secondary">
            {vplc.frameFormat} · {vplc.ipAddress}:{vplc.startPort}{vplc.portCount > 1 ? `~${vplc.startPort + vplc.portCount - 1}` : ''}
          </Typography>
        )}
        <Box sx={{ flex: 1 }} />
        <IconButton size="small" aria-label={t('i18n.tc.vplc.prev', '앞 쪽')} disabled={start === 0} onClick={() => go(start - size)}><NavigateBeforeIcon /></IconButton>
        <Typography variant="body2">{memType && `${addressLabel(memType, start)} ~ ${addressLabel(memType, start + size - 1)}`}</Typography>
        <IconButton size="small" aria-label={t('i18n.tc.vplc.next', '다음 쪽')} disabled={start + size > max} onClick={() => go(start + size)}><NavigateNextIcon /></IconButton>
      </Stack>
      {!vplcs.length && !error && <Alert severity="info">{t('i18n.tc.vplc.nonePublished', '게시된 가상 PLC 가 없습니다 — 가상 PLC 목록에서 게시하세요.')}</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro rows={rows} columns={columns} getRowId={(r) => r.addr} density="compact" hideFooter disableRowSelectionOnClick
            showColumnVerticalBorder showCellVerticalBorder
            processRowUpdate={async (next: Row, prev: Row) => {
              const w = Number(next.word);
              if (!Number.isInteger(w) || w < -32768 || w > 65535) { showToast(t('i18n.tc.vplc.wordRule', '-32768~65535 정수(0x 로 시작하면 16진)를 쉼표로'), 'error'); return prev; }
              if (w !== prev.word) await writeWords(next.addr, [w]);
              return { ...next, word: (w << 16) >> 16 };
            }}
            onProcessRowUpdateError={(e) => showToast(extractApiError(e), 'error')} />
        </Box>
      </Box>
      <WriteDialog open={writeOpen} memoryType={memType} start={start} onClose={() => setWriteOpen(false)} onWrite={write} />
    </PageContainer>
  );
}
