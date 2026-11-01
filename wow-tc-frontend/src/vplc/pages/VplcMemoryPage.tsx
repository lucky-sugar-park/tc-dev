import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogContent, IconButton, ListSubheader, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip,
  Typography,
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
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  CommandBarActions, DialogFooter, DialogTitleClose, DraggablePaper, FilterBar, PageContainer, ScreenAction, extractApiError, showConfirm, showToast,
  useI18n,
} from 'wow-mes-frontend-shared';
import { usePerm } from '../../shared/usePerm';
import { OpcUaTagPanel } from '../components/OpcUaTagPanel';
import {
  MEMORY_TYPES, MODBUS_TABLES, PROTOCOL_LABEL, STATUS_COLOR, addressLabel, isPublished, protocolOf, vplcApi, type VplcControl,
} from '../services/vplcApi';
import type { ModbusTable, Vplc, VplcProtocol } from '../types/vplc';

const SCREEN = 'tc.vplc.memory';
const PAGE_SIZES = [20, 50, 100];
const REFRESH = [0, 1000, 2000, 5000];

type Row = { addr: number; word: number; text: string };

const hex4 = (w: number) => (w & 0xFFFF).toString(16).toUpperCase().padStart(4, '0');
const signed = (w: number) => (w << 16) >> 16;
const ch = (b: number) => (b >= 0x20 && b <= 0x7E ? String.fromCharCode(b) : '·');

/** 메모리 영역 — MC 메모리 종류 또는 Modbus 영역. bit 이면 한 줄(워드)이 비트 16개 */
type Area = { code: string; label: string; max: number; hex?: boolean; bit?: boolean };

/** Modbus 주소(프로토콜 주소, 0부터) — 레지스터는 칸 번호, 비트 영역은 그 줄의 비트 번호 */
const modbusLabel = (area: Area, addr: number, bit = 0) => `${area.code.toUpperCase()}${area.bit ? addr * 16 + bit : addr}`;

/** 값 쓰기 — 워드 값 여러 개(쉼표·공백, 10진·0x16진) 또는 문자열(ASCII, 한 워드에 두 글자) */
function WriteDialog({ open, memoryType, start, label, textHelp, onClose, onWrite }: {
  open: boolean;
  memoryType: string;
  start: number;
  /** 주소 표기 — D100, HR0 */
  label: (addr: number) => string;
  textHelp: string;
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
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.vplc.write', '값 쓰기')} — {memoryType.replace('*', '').toUpperCase()}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <ToggleButtonGroup exclusive size="small" value={mode} onChange={(_e, m) => m && setMode(m)}>
            <ToggleButton value="words">{t('i18n.tc.vplc.modeWords', '워드 값')}</ToggleButton>
            <ToggleButton value="text">{t('i18n.tc.vplc.modeText', '문자열(ASCII)')}</ToggleButton>
          </ToggleButtonGroup>
          <TextField label={t('i18n.tc.vplc.startAddress', '시작 주소')} type="number" value={addr} onChange={(e) => setAddr(e.target.value)}
            helperText={label(Number(addr) || 0)} />
          <TextField label={mode === 'words' ? t('i18n.tc.vplc.words', '값(쉼표로 여러 개)') : t('i18n.tc.vplc.text', '문자열')} value={value} autoFocus
            onChange={(e) => setValue(e.target.value)}
            helperText={mode === 'words' ? t('i18n.tc.vplc.wordsHelp', '예: 1234, -1, 0x00FF — 차례로 이어진 주소에 쓴다') : textHelp} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.tc.vplc.write', '값 쓰기')}</Button></DialogFooter>
    </Dialog>
  );
}

/**
 * 가상 PLC 메모리 보기·제어 (docs/frontend-design.md §5, docs/virtual-devices.md) — 게시된 가상 PLC(종류별로 묶음)를 고른다.
 * 시작·일시정지(무응답)·재개·정지는 모든 종류에.
 * - MELSEC·Modbus: 메모리 종류(영역)·시작 주소·쪽 크기로 표(주소·10진·16진·비트·ASCII), 10진 칸을 고쳐 쓰기·비트를 눌러 켜고 끄기·
 *   여러 값/문자열 쓰기, 종류 전체 지우기. Modbus 코일·입력 비트는 16개를 한 줄로 묶고, 문자열은 높은 바이트가 앞 글자(Modbus 보통 순서),
 *   번호는 프로토콜 주소(0부터).
 * - OPC UA: 태그 표(값 고쳐 쓰기 — 실행 중일 때, 태그 더하기·지우기 — 정의는 vplc DB 에 남는다).
 * 자동 새로 고침은 모두에.
 */
export default function VplcMemoryPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const canWrite = perm('U');
  const canControl = perm('X');
  const active = useIsActiveScreen();
  // 목록에서 ?id= 로 들어오면 그 가상 PLC — 고른 것을 주소에 다시 쓰지 않는다(주소가 바뀌면 탭이 하나 더 열린다)
  const [params] = useSearchParams();
  const [devices, setDevices] = useState<Vplc[]>([]);
  const [deviceKey, setDeviceKey] = useState(params.get('id') ?? '');
  const [memType, setMemType] = useState('');
  const [start, setStart] = useState(0);
  const [startInput, setStartInput] = useState('0');
  const [size, setSize] = useState(20);
  const [refresh, setRefresh] = useState(0);
  const [words, setWords] = useState<number[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [writeOpen, setWriteOpen] = useState(false);
  const [tagAddOpen, setTagAddOpen] = useState(false);
  const [tagSearch, setTagSearch] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const device = devices.find((d) => d.id === deviceKey) ?? null;
  const protocol = device ? protocolOf(device) : null;
  /** MELSEC 이면 그 PLC */
  const vplc = protocol === 'MELSEC_MC' ? device : null;
  /** Modbus 이면 그 장치 */
  const mb = protocol === 'MODBUS_TCP' ? device : null;
  const mbSize = Number(mb?.settings?.size ?? 10000);

  const loadDevices = useCallback(async () => {
    try {
      // 종류(MELSEC → Modbus → OPC UA)·이름 차례 — 처음 고르는 것은 MELSEC
      const order: VplcProtocol[] = ['MELSEC_MC', 'MODBUS_TCP', 'OPC_UA'];
      const list = (await vplcApi.list()).filter((v) => isPublished(v.status))
        .sort((a, b) => order.indexOf(protocolOf(a)) - order.indexOf(protocolOf(b)) || a.name.localeCompare(b.name));
      setDevices(list);
      setDeviceKey((cur) => (cur && list.some((d) => d.id === cur) ? cur : list[0]?.id ?? ''));
    } catch (e) {
      setError(extractApiError(e));
    }
  }, []);
  useEffect(() => { if (active) void loadDevices(); }, [active, loadDevices]);

  const areas = useMemo<Area[]>(() => {
    if (vplc) {
      return vplc.memoryTypeList.map((c) => {
        const m = MEMORY_TYPES.find((x) => x.code === c);
        return { code: c, label: c.replace('*', ''), max: m?.max ?? 0xFFFFF, hex: m?.hex, bit: m?.bit };
      });
    }
    if (mb) {
      return MODBUS_TABLES.map((x) => ({ code: x.code, label: x.code.toUpperCase(), bit: x.bit, max: x.bit ? Math.ceil(mbSize / 16) - 1 : mbSize - 1 }));
    }
    return [];
  }, [vplc, mb, mbSize]);
  const area = areas.find((a) => a.code === memType) ?? null;

  // 장비를 바꾸면 그 장비의 첫 메모리 종류로
  useEffect(() => {
    if (!areas.length) return;
    setMemType((cur) => (areas.some((a) => a.code === cur) ? cur : areas[0].code));
  }, [areas]);

  /** 주소 표기 — 줄(워드) 번호, 비트 영역 비트 번호 */
  const rowLabel = useCallback((addr: number) => {
    if (!area) return String(addr);
    return mb ? modbusLabel(area, addr) : addressLabel(area.code, addr);
  }, [area, mb]);
  const bitLabel = useCallback((addr: number, b: number) => {
    if (!area) return `b${b}`;
    if (mb) return area.bit ? modbusLabel(area, addr, b) : `b${b}`;
    return area.bit ? addressLabel(area.code, addr * 16 + b) : `b${b}`;
  }, [area, mb]);

  const read = useCallback(async () => {
    if (!area || (!vplc && !mb)) { setWords(null); return; }
    try {
      if (vplc) {
        setWords((await vplcApi.read(vplc, area.code, start, size)).wordInfos.map((w) => w.word));
      } else if (mb) {
        const table = area.code as ModbusTable;
        const rowsN = Math.max(0, Math.min(size, area.max - start + 1));
        if (area.bit) {
          const first = start * 16;
          const count = Math.min(rowsN * 16, mbSize - first);
          const bits = count > 0 ? (await vplcApi.modbusRead(mb.id, table, first, count)).values : [];
          setWords(Array.from({ length: rowsN }, (_, r) => {
            let w = 0;
            for (let b = 0; b < 16; b++) if (bits[r * 16 + b] === true) w |= 1 << b;
            return signed(w);
          }));
        } else {
          setWords(rowsN > 0 ? (await vplcApi.modbusRead(mb.id, table, start, rowsN)).values.map((v) => signed(Number(v))) : []);
        }
      }
      setError(null);
    } catch (e) {
      setError(extractApiError(e));
    }
  }, [vplc, mb, mbSize, area, start, size]);
  const readRef = useRef(read);
  readRef.current = read;
  useEffect(() => { if (active) void read(); }, [active, read]);
  useEffect(() => {
    if (!active || !refresh || protocol === 'OPC_UA') return undefined;
    const h = window.setInterval(() => { void readRef.current(); }, refresh);
    return () => window.clearInterval(h);
  }, [active, refresh, protocol]);

  const max = area?.max ?? 0xFFFFF;
  const go = (s: number) => { const n = Math.max(0, Math.min(s, max)); setStart(n); setStartInput(String(n)); };

  /** 워드 쓰기 — Modbus 비트 영역은 한 워드를 비트 16개로 푼다(영역 끝에서 자른다) */
  const putWords = useCallback(async (addr: number, ws: number[]) => {
    if (!area) return;
    if (vplc) {
      await vplcApi.writeWords(vplc, area.code, addr, ws);
    } else if (mb) {
      const table = area.code as ModbusTable;
      if (area.bit) {
        const bits = ws.flatMap((w) => Array.from({ length: 16 }, (_, b) => ((w >> b) & 1) === 1)).slice(0, Math.max(0, mbSize - addr * 16));
        await vplcApi.modbusWrite(mb.id, table, addr * 16, bits);
      } else {
        await vplcApi.modbusWrite(mb.id, table, addr, ws);
      }
    }
  }, [vplc, mb, mbSize, area]);

  const writeWords = useCallback(async (addr: number, ws: number[]) => {
    try {
      await putWords(addr, ws);
      await readRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  }, [putWords]);

  const write = async (addr: number, input: { words?: number[]; text?: string }) => {
    if (!area) return;
    try {
      if (input.words) {
        await putWords(addr, input.words);
      } else if (input.text && vplc) {
        await vplcApi.writeText(vplc, area.code, addr, input.text);
      } else if (input.text) {
        // Modbus — 높은 바이트가 앞 글자
        const codes = Array.from(input.text, (c) => c.charCodeAt(0));
        await putWords(addr, Array.from({ length: Math.ceil(codes.length / 2) }, (_, i) => (codes[2 * i] << 8) | (codes[2 * i + 1] ?? 0)));
      }
      setWriteOpen(false);
      showToast(t('i18n.tc.vplc.written', '썼습니다'), 'success');
      await readRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  const confirmPause = () => showConfirm({
    message: t('i18n.tc.vplc.confirmPause', '일시정지하면 연결은 두고 요청에 답하지 않습니다(tc 시간 초과 시험). 멈출까요?'), severity: 'warning',
  });

  const control = async (op: VplcControl) => {
    if (op === 'pause' && !(await confirmPause())) return;
    if (!device) return;
    try {
      await vplcApi.control(op, device.id);
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
    await loadDevices();
  };

  const clearAll = async () => {
    if (!area) return;
    const ok = await showConfirm({
      message: t('i18n.tc.vplc.confirmClear', '{type} 메모리를 모두 0 으로 지울까요?').replace('{type}', area.label), severity: 'error',
    });
    if (!ok) return;
    try {
      if (vplc) await vplcApi.clearAll(vplc, area.code, start);
      else if (mb) await vplcApi.modbusWrite(mb.id, area.code as ModbusTable, 0, Array.from({ length: mbSize }, () => (area.bit ? false : 0)));
      await readRef.current();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  // ASCII — MC 는 낮은 바이트가 앞 글자, Modbus 는 높은 바이트가 앞 글자
  const rows = useMemo<Row[]>(() => (words ?? []).map((w, i) => {
    const lo = w & 0xFF;
    const hi = (w >> 8) & 0xFF;
    return { addr: start + i, word: w, text: mb ? ch(hi) + ch(lo) : ch(lo) + ch(hi) };
  }), [words, start, mb]);

  const columns = useMemo<GridColDef<Row>[]>(() => [
    { field: 'addr', headerName: t('i18n.tc.vplc.address', '주소'), width: 110, valueFormatter: (v: number) => rowLabel(v) },
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
            const label = bitLabel(row.addr, b);
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
  ], [t, canWrite, rowLabel, bitLabel, writeWords]);

  const opc = protocol === 'OPC_UA';
  const lastRow = Math.min(start + size - 1, max);
  // Modbus 비트 영역은 마지막 줄이 영역 끝에서 잘린다(칸 1000 이면 CO999 까지)
  const rangeText = !area ? '' : area.bit && mb
    ? `${rowLabel(start)} ~ ${area.code.toUpperCase()}${Math.min(lastRow * 16 + 15, mbSize - 1)}`
    : `${rowLabel(start)} ~ ${rowLabel(lastRow)}`;
  const kindOf = (k: VplcProtocol) => devices.filter((d) => protocolOf(d) === k);
  /** 고른 가상 PLC 한 줄 설명 */
  const caption = !device ? '' : protocol === 'MODBUS_TCP'
    ? `Modbus TCP · ${device.ipAddress}:${device.startPort} · ${t('i18n.tc.vplc.unitId', '단위 ID')} ${String(device.settings?.unitId ?? 1)} · ${String(device.settings?.delayMs ?? 0)}ms`
    : protocol === 'OPC_UA'
      ? `OPC UA · opc.tcp://${device.ipAddress}:${device.startPort}${String(device.settings?.path ?? '')}`
      : `${device.frameFormat} · ${device.ipAddress}:${device.startPort}${device.portCount > 1 ? `~${device.startPort + device.portCount - 1}` : ''}`;
  const onTagError = useCallback((m: string | null) => setError(m), []);

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void loadDevices(); if (opc) setReloadKey((k) => k + 1); else void read(); }} />
        {canControl && device && (device.status === 'RUNNING'
          ? (
            <>
              <ScreenAction icon={<PauseIcon fontSize="small" />} label={t('i18n.tc.vplc.pause', '일시정지')} onClick={() => { void control('pause'); }} />
              <ScreenAction icon={<StopIcon fontSize="small" />} label={t('i18n.tc.vplc.stop', '정지')} onClick={() => { void control('stop'); }} />
            </>
          )
          : device.status === 'PAUSED'
            ? <ScreenAction icon={<PlayArrowIcon fontSize="small" />} label={t('i18n.tc.connector.resume', '재개')} onClick={() => { void control('resume'); }} />
            : <ScreenAction icon={<PlayArrowIcon fontSize="small" />} label={t('i18n.tc.vplc.start', '시작')} onClick={() => { void control('start'); }} />)}
        {canWrite && (vplc || mb) && (
          <>
            <ScreenAction icon={<EditIcon fontSize="small" />} label={t('i18n.tc.vplc.write', '값 쓰기')} onClick={() => setWriteOpen(true)} />
            <ScreenAction icon={<DeleteSweepIcon fontSize="small" />} label={t('i18n.tc.vplc.clearAll', '모두 지우기')} onClick={() => { void clearAll(); }} />
          </>
        )}
        {perm('C') && opc && (
          <ScreenAction icon={<PlaylistAddIcon fontSize="small" />} label={t('i18n.tc.vplc.tagAdd', '태그 추가')} onClick={() => setTagAddOpen(true)} />
        )}
      </CommandBarActions>
      <FilterBar onSearch={() => { if (opc) setReloadKey((k) => k + 1); else go(Number(startInput) || 0); }}
        onReset={() => { go(0); setSize(20); setTagSearch(''); }}>
        <TextField select size="small" label={t('i18n.tc.vplc.title', '가상 PLC')} value={device ? deviceKey : ''} sx={{ width: 220 }}
          onChange={(e) => setDeviceKey(e.target.value)}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.title', '가상 PLC') } }}>
          {(['MELSEC_MC', 'MODBUS_TCP', 'OPC_UA'] as const).flatMap((k) => (kindOf(k).length
            ? [<ListSubheader key={`h-${k}`}>{PROTOCOL_LABEL[k]}</ListSubheader>, ...kindOf(k).map((d) => <MenuItem key={d.id} value={d.id}>{d.name}</MenuItem>)]
            : []))}
        </TextField>
        {opc ? (
          <TextField size="small" label={t('i18n.tc.vplc.tagSearch', '태그 검색')} value={tagSearch} sx={{ width: 200 }}
            onChange={(e) => setTagSearch(e.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        ) : (
          <>
            <TextField select size="small" label={t('i18n.tc.vplc.memoryType', '메모리')} value={area ? memType : ''} sx={{ width: 120 }}
              onChange={(e) => { setMemType(e.target.value); go(0); }}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.memoryType', '메모리') } }}>
              {areas.map((a) => <MenuItem key={a.code} value={a.code}>{a.label}</MenuItem>)}
            </TextField>
            <TextField size="small" type="number" label={t('i18n.tc.vplc.startAddress', '시작 주소')} value={startInput} sx={{ width: 130 }}
              onChange={(e) => setStartInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') go(Number(startInput) || 0); }}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.startAddress', '시작 주소'), min: 0 } }} />
            <TextField select size="small" label={t('i18n.tc.vplc.pageSize', '워드 수')} value={size} sx={{ width: 100 }} onChange={(e) => setSize(Number(e.target.value))}
              slotProps={{ inputLabel: { shrink: true } }}>
              {PAGE_SIZES.map((n) => <MenuItem key={n} value={n}>{n}</MenuItem>)}
            </TextField>
          </>
        )}
        <TextField select size="small" label={t('i18n.tc.vplc.autoRefresh', '자동 새로 고침')} value={refresh} sx={{ width: 140 }}
          onChange={(e) => setRefresh(Number(e.target.value))}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': t('i18n.tc.vplc.autoRefresh', '자동 새로 고침') } }}>
          {REFRESH.map((n) => <MenuItem key={n} value={n}>{n ? `${n / 1000}s` : t('i18n.tc.vplc.off', '끔')}</MenuItem>)}
        </TextField>
      </FilterBar>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', px: 0.5 }}>
        {device && <Chip size="small" color={STATUS_COLOR[device.status ?? ''] ?? 'default'} label={device.status} />}
        {device && <Typography variant="caption" color="text.secondary">{caption}</Typography>}
        {mb && (
          <Typography variant="caption" color="text.secondary">
            · {t('i18n.tc.vplc.modbusAddrNote', '번호는 프로토콜 주소(0부터) — tc 주소 HR1 은 여기 HR0')}
          </Typography>
        )}
        <Box sx={{ flex: 1 }} />
        {!opc && (
          <>
            <IconButton size="small" aria-label={t('i18n.tc.vplc.prev', '앞 쪽')} disabled={start === 0} onClick={() => go(start - size)}><NavigateBeforeIcon /></IconButton>
            <Typography variant="body2">{rangeText}</Typography>
            <IconButton size="small" aria-label={t('i18n.tc.vplc.next', '다음 쪽')} disabled={start + size > max} onClick={() => go(start + size)}><NavigateNextIcon /></IconButton>
          </>
        )}
      </Stack>
      {!devices.length && !error && (
        <Alert severity="info">{t('i18n.tc.vplc.nonePublished', '게시된 가상 PLC 가 없습니다 — 가상 PLC 목록에서 게시하세요.')}</Alert>
      )}
      {error && <Alert severity="error">{error}</Alert>}
      {opc ? (
        <OpcUaTagPanel vplcId={device?.id ?? ''} running={device?.status === 'RUNNING' || device?.status === 'PAUSED'} active={active} search={tagSearch} reloadKey={reloadKey} refreshMs={refresh} canWrite={canWrite} canDelete={perm('D')}
          addOpen={tagAddOpen} onAddClose={() => setTagAddOpen(false)} onError={onTagError} />
      ) : (
        <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
          <Box sx={{ position: 'absolute', inset: 0 }}>
            <DataGridPro rows={rows} columns={columns} getRowId={(r) => r.addr} density="compact" hideFooter disableRowSelectionOnClick
              showColumnVerticalBorder showCellVerticalBorder
              processRowUpdate={async (next: Row, prev: Row) => {
                const w = Number(next.word);
                if (!Number.isInteger(w) || w < -32768 || w > 65535) { showToast(t('i18n.tc.vplc.wordRule', '-32768~65535 정수(0x 로 시작하면 16진)를 쉼표로'), 'error'); return prev; }
                if (w !== prev.word) await writeWords(next.addr, [w]);
                return { ...next, word: signed(w) };
              }}
              onProcessRowUpdateError={(e) => showToast(extractApiError(e), 'error')} />
          </Box>
        </Box>
      )}
      <WriteDialog open={writeOpen} memoryType={memType} start={start} label={rowLabel} onClose={() => setWriteOpen(false)} onWrite={write}
        textHelp={mb ? t('i18n.tc.vplc.textHelpModbus', '한 레지스터에 두 글자 — 높은 바이트가 앞 글자(Modbus 보통 순서)')
          : t('i18n.tc.vplc.textHelp', '한 워드에 두 글자 — 낮은 바이트가 앞 글자(MC 와 같다)')} />
    </PageContainer>
  );
}
