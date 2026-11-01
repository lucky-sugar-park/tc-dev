import { useCallback, useEffect, useMemo, useRef, useState, type ReactElement, type ReactNode } from 'react';
import {
  Alert, Autocomplete, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, FormControlLabel, IconButton, LinearProgress, Radio,
  RadioGroup, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import {
  DataGridPro, GridGetRowsError, useGridApiRef, type DataGridProProps, type GridColDef, type GridDataSource, type GridValidRowModel,
} from '@mui/x-data-grid-pro';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import {
  DialogTitleClose, DraggablePaper, downloadExcel, excelErrorMessage, fileStamp, showToast, useI18n, type ExcelRow,
} from 'wow-mes-frontend-shared';
import { ListTitle } from './ListTitle';
import { MoreActionsIcon, withActionTooltips } from './actionTooltips';

/**
 * 큰 목록의 구간 스크롤 표 (docs/range-grid-design.md) — "C + 구간 이동".
 *
 * <ul>
 *   <li>MUI 지연 로딩(dataSource + lazyLoading, VIEWPORT): 보이는 구간만 서버에 요청한다. 아래 쪽 넘김은 없다.</li>
 *   <li>MUI 는 받은 행을 지우지 않고, 받지 않은 자리마다 스켈레톤을 rowCount 만큼 만든다 — 그래서 그리드는 <b>한 구간
 *       ({@link SEGMENT_SIZE})만</b> 다룬다. 넘는 결과는 제목 줄의 이전·다음 구간으로 옮긴다(받은 행은 비운다).</li>
 *   <li>서버 규약: start·limit(절대 위치), asOf(스냅샷 — 첫 요청이 정하고 이후 그대로), withTotal(첫 요청만 건수).</li>
 *   <li>번호 칸은 검색 결과 안의 절대 위치(최신 = 1) — 구간을 옮겨도 이어진다.</li>
 * </ul>
 */

/** 그리드가 한 번에 다루는 행 수(구간) — 스켈레톤·받은 행의 상한 */
export const SEGMENT_SIZE = 100_000;

/** 브라우저에서 .xlsx 로 만드는 최대 건수 */
export const EXPORT_MAX = 200_000;

/** 한 번에 받는 행 수 — 고를 값과 직접 입력 범위(서버 상한 1000) */
const CHUNK_OPTIONS = ['50', '100', '200'];
const CHUNK_MIN = 10;
const CHUNK_MAX = 1000;

/** 엑셀을 만들 때 한 번에 받는 행 수 */
const EXPORT_CHUNK = 1000;

export type RangeParams = { start: number; limit: number; asOf?: number; withTotal: boolean };

export type RangePage<T> = { rows: T[]; total: number | null; asOf: number };

/** 서버 구간 조회 — extra 는 엑셀의 추가 선택(예: 본문 포함) */
export type RangeFetch<T> = (p: RangeParams, extra?: Record<string, boolean>) => Promise<RangePage<T>>;

/** 번호 칸 값이 붙은 행 */
export type Numbered<T> = T & { _no: number };

const clampChunk = (n: number) => Math.min(CHUNK_MAX, Math.max(CHUNK_MIN, Math.round(n)));

export function useRangeGrid<T extends GridValidRowModel>(fetchRange: RangeFetch<T>, initialChunk = 100) {
  const apiRef = useGridApiRef();
  const [chunk, setChunkState] = useState(initialChunk);
  const [total, setTotal] = useState<number | null>(null);
  const [segment, setSegment] = useState(0);
  const [padNonce, setPadNonce] = useState(0);
  const fetchRef = useRef(fetchRange);
  fetchRef.current = fetchRange;
  const asOfRef = useRef<number | undefined>(undefined);
  const totalRef = useRef<number | null>(null);
  const segmentRef = useRef(0);
  const resetSeqRef = useRef(0);
  const padDoneForRef = useRef(-1);

  const reload = useCallback(() => {
    resetSeqRef.current += 1;
    apiRef.current?.dataSource.cache.clear();
    apiRef.current?.scroll({ top: 0 });
    void apiRef.current?.dataSource.fetchRows();
  }, [apiRef]);

  /** 조건 바꿈·새로 고침 — 스냅샷·건수·구간을 버리고 처음부터 */
  const refresh = useCallback(() => {
    asOfRef.current = undefined;
    totalRef.current = null;
    segmentRef.current = 0;
    setSegment(0);
    setTotal(null);
    reload();
  }, [reload]);

  /** 구간 옮기기 — 같은 스냅샷·건수로 그 구간의 처음부터 */
  const goSegment = useCallback((index: number) => {
    const t = totalRef.current ?? 0;
    const last = Math.max(0, Math.ceil(t / SEGMENT_SIZE) - 1);
    const next = Math.min(Math.max(0, index), last);
    if (next === segmentRef.current) return;
    segmentRef.current = next;
    setSegment(next);
    reload();
  }, [reload]);

  /** 한 번에 받는 행 수 — 바꾸면 처음부터(MUI lazyLoader 는 paginationModel 바뀜을 구독하지 않는다) */
  const setChunk = useCallback((n: number) => {
    const v = clampChunk(n);
    setChunkState(v);
    apiRef.current?.setPaginationModel({ page: 0, pageSize: v });
    refresh();
  }, [apiRef, refresh]);

  const getRows = useCallback(async (params: Parameters<GridDataSource['getRows']>[0]) => {
    const start = typeof params.start === 'number' ? params.start : Number(params.start) || 0;
    const end = params.end ?? start; // MUI end 는 마지막 위치를 포함한다
    const limit = Math.max(1, end - start + 1);
    const seq = resetSeqRef.current;
    const base = segmentRef.current * SEGMENT_SIZE;
    const res = await fetchRef.current({ start: base + start, limit, asOf: asOfRef.current, withTotal: asOfRef.current === undefined });
    const rows = res.rows.map((r, i) => ({ ...r, _no: base + start + i + 1 })) as Record<string, unknown>[];
    // 새로 고침 전에 보낸 늦은 응답 — 행은 그리드가 요청 순번으로 거르고, 건수·스냅샷은 여기서 버린다
    if (seq !== resetSeqRef.current) return { rows, rowCount: Math.max(0, Math.min((totalRef.current ?? 0) - base, SEGMENT_SIZE)) };
    if (asOfRef.current === undefined) asOfRef.current = res.asOf;
    if (res.total !== null && res.total !== undefined) {
      totalRef.current = res.total;
      setTotal(res.total);
    }
    // MUI v9: 리셋 뒤 첫 응답에서는 rowCount 가 아직 반영되지 않아 스켈레톤을 채우지 못한다 — dataSource 를 한 번 바꿔 다시 돌게 한다
    if (padDoneForRef.current !== resetSeqRef.current) {
      padDoneForRef.current = resetSeqRef.current;
      setTimeout(() => setPadNonce((n) => n + 1), 0);
    }
    return { rows, rowCount: Math.max(0, Math.min((totalRef.current ?? 0) - base, SEGMENT_SIZE)) };
  }, []);

  const dataSource = useMemo<GridDataSource>(() => ({ getRows }), [getRows, padNonce]); // eslint-disable-line react-hooks/exhaustive-deps

  /** 엑셀용 — 같은 스냅샷으로 [from, to) 를 나눠 받는다. cancelled() 가 true 면 멈춘다 */
  const fetchAll = useCallback(async (from: number, to: number, extra: Record<string, boolean>, onProgress: (n: number) => void,
    cancelled: () => boolean): Promise<T[] | null> => {
    const out: T[] = [];
    for (let s = from; s < to; s += EXPORT_CHUNK) {
      if (cancelled()) return null;
      const page = await fetchRef.current({ start: s, limit: Math.min(EXPORT_CHUNK, to - s), asOf: asOfRef.current, withTotal: false }, extra);
      out.push(...page.rows);
      onProgress(out.length);
      if (page.rows.length === 0) break;
    }
    return out;
  }, []);

  return { apiRef, dataSource, chunk, setChunk, total, segment, goSegment, refresh, fetchAll };
}

export type RangeGridState<T extends GridValidRowModel> = ReturnType<typeof useRangeGrid<T>>;

/** 제목 줄 오른쪽 — 구간 이동과 한 번에 받는 행 수 */
function RangeControls<T extends GridValidRowModel>({ grid }: { grid: RangeGridState<T> }): ReactElement {
  const { t } = useI18n();
  const [text, setText] = useState(String(grid.chunk));
  useEffect(() => { setText(String(grid.chunk)); }, [grid.chunk]);
  const commit = (v: string) => {
    const n = Number(v);
    if (!Number.isFinite(n) || n <= 0) { setText(String(grid.chunk)); return; }
    if (clampChunk(n) !== grid.chunk) grid.setChunk(n); else setText(String(grid.chunk));
  };
  const total = grid.total ?? 0;
  const segments = Math.ceil(total / SEGMENT_SIZE);
  const from = grid.segment * SEGMENT_SIZE + 1;
  const to = Math.min(total, (grid.segment + 1) * SEGMENT_SIZE);
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      {segments > 1 && (
        <Stack direction="row" sx={{ alignItems: 'center' }} aria-label="range-segment">
          <Tooltip title={t('i18n.tc.range.prevSegment', '이전 구간')}>
            <span><IconButton size="small" disabled={grid.segment === 0} onClick={() => grid.goSegment(grid.segment - 1)}
              aria-label={t('i18n.tc.range.prevSegment', '이전 구간')}><ChevronLeftIcon fontSize="small" /></IconButton></span>
          </Tooltip>
          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
            {from.toLocaleString()}~{to.toLocaleString()} / {total.toLocaleString()}
          </Typography>
          <Tooltip title={t('i18n.tc.range.nextSegment', '다음 구간')}>
            <span><IconButton size="small" disabled={grid.segment >= segments - 1} onClick={() => grid.goSegment(grid.segment + 1)}
              aria-label={t('i18n.tc.range.nextSegment', '다음 구간')}><ChevronRightIcon fontSize="small" /></IconButton></span>
          </Tooltip>
        </Stack>
      )}
      <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>{t('i18n.tc.range.chunk', '한 번에 받는 행 수')}</Typography>
      <Autocomplete
        freeSolo forcePopupIcon disableClearable size="small" options={CHUNK_OPTIONS} value={String(grid.chunk)} inputValue={text}
        onInputChange={(_e, v) => setText(v)} onChange={(_e, v) => commit(String(v))}
        sx={{ width: 100 }}
        renderInput={(p) => (
          <TextField {...p} onBlur={() => commit(text)}
            // 라벨·테두리 없이 — 왼쪽 글이 라벨이다
            // 높이도 낮춰 제목 줄이 표에 붙게
            sx={{ '& .MuiOutlinedInput-notchedOutline': { border: 'none' }, '& .MuiInputBase-input': { textAlign: 'right' }, '& .MuiInputBase-root': { height: 28, py: 0 } }}
            slotProps={{ ...p.slotProps, htmlInput: { ...p.slotProps.htmlInput, 'aria-label': t('i18n.tc.range.chunk', '한 번에 받는 행 수'), inputMode: 'numeric' } }} />
        )}
      />
    </Stack>
  );
}

/**
 * 구간 스크롤 표 — 제목·건수(글머리)·구간 이동·한 번에 받는 행 수, 번호 칸, 아래 쪽 넘김 없음.
 * 그 밖의 DataGrid 속성(행 누름·고르기 등)은 gridProps 로.
 */
export function RangeGrid<T extends GridValidRowModel>({ grid, title, columns, getRowId, gridProps }: {
  grid: RangeGridState<T>;
  title: string;
  columns: GridColDef<T>[];
  getRowId: (r: T) => string | number;
  gridProps?: Partial<DataGridProProps<Numbered<T>>>;
}): ReactElement {
  const { t } = useI18n();
  const cols = useMemo(() => withActionTooltips([
    { field: '_no', headerName: t('i18n.tc.schedule.no', '번호'), width: 90, type: 'number', sortable: false, filterable: false,
      valueFormatter: (v: number) => (v == null ? '' : v.toLocaleString()) } as GridColDef<T>,
    ...columns,
  ]), [columns, t]);
  return (
    // 제목 줄과 표는 붙여 둔다(페이지의 다른 칸 사이 간격 8px 보다 좁게)
    <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', gap: 0.5 }}>
      <ListTitle title={title} count={grid.total ?? 0}>
        <RangeControls grid={grid} />
      </ListTitle>
      <Box sx={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <Box sx={{ position: 'absolute', inset: 0 }}>
          <DataGridPro<Numbered<T>>
            apiRef={grid.apiRef}
            columns={cols as GridColDef<Numbered<T>>[]}
            getRowId={(r) => getRowId(r)}
            dataSource={grid.dataSource}
            dataSourceCache={null}
            lazyLoading
            // 작업 칸(field 'actions')은 오른쪽에 고정
            initialState={{ pagination: { paginationModel: { page: 0, pageSize: grid.chunk } }, pinnedColumns: { left: [], right: ['actions'] } }}
            onDataSourceError={(e) => showToast(e instanceof GridGetRowsError ? (e.cause instanceof Error ? e.cause.message : e.message) : String(e), 'error')}
            hideFooter density="compact" showColumnVerticalBorder showCellVerticalBorder disableRowSelectionOnClick
            disableColumnSorting disableColumnFilter
            slots={{ moreActionsIcon: MoreActionsIcon }}
            getRowClassName={(p) => (p.indexRelativeToCurrentPage % 2 === 0 ? 'row-even' : 'row-odd')}
            {...gridProps}
          />
        </Box>
      </Box>
    </Box>
  );
}

/** 엑셀의 추가 선택 — 예: 송수신 기록의 본문 포함 */
export type ExportOption = { key: string; label: string; help?: string };

/**
 * 엑셀 내려받기 — 지금 구간 또는 검색 결과 전체를, 화면과 같은 조건·스냅샷으로 서버에서 나눠 받아 .xlsx 로 만든다.
 * {@link EXPORT_MAX} 건을 넘으면 만들지 않고 조건을 좁히라고 알린다.
 */
export function RangeExportDialog<T extends GridValidRowModel>({ open, onClose, grid, fileName, sheetName, toRow, options }: {
  open: boolean;
  onClose: () => void;
  grid: RangeGridState<T>;
  fileName: string;
  sheetName: string;
  toRow: (r: T, no: number) => ExcelRow;
  options?: ExportOption[];
}): ReactElement {
  const { t } = useI18n();
  const [scope, setScope] = useState<'segment' | 'all'>('all');
  const [extra, setExtra] = useState<Record<string, boolean>>({});
  const [done, setDone] = useState<number | null>(null);
  const cancelRef = useRef(false);
  const total = grid.total ?? 0;
  const segFrom = grid.segment * SEGMENT_SIZE;
  const segTo = Math.min(total, segFrom + SEGMENT_SIZE);
  const [from, to] = scope === 'segment' ? [segFrom, segTo] : [0, total];
  const count = Math.max(0, to - from);
  const tooMany = count > EXPORT_MAX;
  const running = done !== null;

  useEffect(() => { if (open) { setDone(null); cancelRef.current = false; setScope(total > SEGMENT_SIZE ? 'segment' : 'all'); } }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const close = () => { cancelRef.current = true; setDone(null); onClose(); };

  const start = async () => {
    cancelRef.current = false;
    setDone(0);
    try {
      const rows = await grid.fetchAll(from, to, extra, setDone, () => cancelRef.current);
      if (!rows) return;
      await downloadExcel({ fileName: `${fileName}_${fileStamp()}`, sheets: [{ name: sheetName, rows: rows.map((r, i) => toRow(r, from + i + 1)) }] });
      onClose();
    } catch (e) {
      showToast(excelErrorMessage(e), 'error');
    } finally {
      setDone(null);
    }
  };

  const info: ReactNode = tooMany
    ? <Alert severity="warning">{t('i18n.tc.range.exportTooMany', '{n}건은 엑셀로 만들기에 많습니다(최대 {max}건) — 기간 등 조건을 좁히세요.')
      .replace('{n}', count.toLocaleString()).replace('{max}', EXPORT_MAX.toLocaleString())}</Alert>
    : <Typography variant="body2">{t('i18n.tc.range.exportCount', '{n}건을 내려받습니다.').replace('{n}', count.toLocaleString())}</Typography>;

  return (
    <Dialog open={open} onClose={running ? undefined : close} maxWidth="xs" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={close}>{t('i18n.common.excelDownload', '엑셀 다운로드')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={1.5}>
          <RadioGroup value={scope} onChange={(_e, v) => setScope(v as 'segment' | 'all')}>
            {total > SEGMENT_SIZE && (
              <FormControlLabel value="segment" disabled={running} control={<Radio size="small" />}
                label={`${t('i18n.tc.range.exportSegment', '지금 구간')} (${(segFrom + 1).toLocaleString()}~${segTo.toLocaleString()})`} />
            )}
            <FormControlLabel value="all" disabled={running} control={<Radio size="small" />}
              label={`${t('i18n.tc.range.exportAll', '검색 결과 전체')} (${total.toLocaleString()})`} />
          </RadioGroup>
          {options?.map((o) => (
            <Box key={o.key}>
              <FormControlLabel disabled={running} control={<Checkbox size="small" checked={!!extra[o.key]}
                onChange={(e) => setExtra({ ...extra, [o.key]: e.target.checked })} />} label={o.label} />
              {o.help && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', pl: 4, mt: -0.5 }}>{o.help}</Typography>}
            </Box>
          ))}
          {info}
          {running && (
            <Box>
              <LinearProgress variant="determinate" value={count ? Math.min(100, (done / count) * 100) : 0} />
              <Typography variant="caption" color="text.secondary">{done.toLocaleString()} / {count.toLocaleString()}</Typography>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={close}>{running ? t('i18n.common.cancel', '취소') : t('i18n.common.close', '닫기')}</Button>
        <Button variant="contained" disabled={running || tooMany || count === 0} onClick={() => { void start(); }}>
          {t('i18n.tc.range.exportStart', '내려받기')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
