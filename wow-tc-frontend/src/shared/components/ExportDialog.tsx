import { useEffect, useRef, useState, type ReactElement, type ReactNode } from 'react';
import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, FormControlLabel, LinearProgress, Radio, RadioGroup, Stack, Typography,
} from '@mui/material';
import {
  DialogTitleClose, DraggablePaper, downloadExcel, excelErrorMessage, fileStamp, showToast, useI18n, type ExcelRow,
} from 'wow-mes-frontend-shared';

/** 브라우저에서 .xlsx 로 만드는 최대 건수 */
export const EXPORT_MAX = 200_000;

/** 엑셀의 추가 선택 — 예: 송수신 기록의 본문 포함 */
export type ExportOption = { key: string; label: string; help?: string };

/**
 * 내려받을 범위 하나 — 이름·첫 번호(0부터의 위치)·건수와 행을 가져오는 방법.
 * load 는 cancelled() 가 true 면 null 을 돌려 멈춘다. 받은 행 수를 onProgress 로 알린다.
 */
export type ExportScope<T> = {
  key: string;
  label: string;
  from: number;
  count: number;
  load: (extra: Record<string, boolean>, onProgress: (n: number) => void, cancelled: () => boolean) => Promise<T[] | null>;
};

/**
 * 엑셀 다운로드 창(목록 화면 공통) — 범위(지금 구간·쪽 / 검색 결과 전체 등)를 고르고, 추가 선택을 하고, 진행률·취소와 함께 .xlsx 로 만든다.
 * {@link EXPORT_MAX} 건을 넘으면 만들지 않고 조건을 좁히라고 알린다. 번호는 범위 안 위치에 첫 번호를 더한 것.
 */
export function ExportDialog<T>({ open, onClose, scopes, defaultScope, fileName, sheetName, toRow, options }: {
  open: boolean;
  onClose: () => void;
  scopes: ExportScope<T>[];
  defaultScope?: string;
  fileName: string;
  sheetName: string;
  toRow: (r: T, no: number) => ExcelRow;
  options?: ExportOption[];
}): ReactElement {
  const { t } = useI18n();
  const [scopeKey, setScopeKey] = useState(defaultScope ?? scopes[scopes.length - 1]?.key ?? '');
  const [extra, setExtra] = useState<Record<string, boolean>>({});
  const [done, setDone] = useState<number | null>(null);
  const cancelRef = useRef(false);
  const scope = scopes.find((s) => s.key === scopeKey) ?? scopes[scopes.length - 1];
  const count = Math.max(0, scope?.count ?? 0);
  const tooMany = count > EXPORT_MAX;
  const running = done !== null;

  useEffect(() => {
    if (open) { setDone(null); cancelRef.current = false; setScopeKey(defaultScope ?? scopes[scopes.length - 1]?.key ?? ''); }
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const close = () => { cancelRef.current = true; setDone(null); onClose(); };

  const start = async () => {
    if (!scope) return;
    cancelRef.current = false;
    setDone(0);
    try {
      const rows = await scope.load(extra, setDone, () => cancelRef.current);
      if (!rows) return;
      await downloadExcel({ fileName: `${fileName}_${fileStamp()}`, sheets: [{ name: sheetName, rows: rows.map((r, i) => toRow(r, scope.from + i + 1)) }] });
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
          <RadioGroup value={scope?.key ?? ''} onChange={(_e, v) => setScopeKey(v)}>
            {scopes.map((s) => (
              <FormControlLabel key={s.key} value={s.key} disabled={running} control={<Radio size="small" />} label={s.label} />
            ))}
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
