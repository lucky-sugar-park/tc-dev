import { useEffect, useRef, useState, type ReactElement } from 'react';
import { Alert, Box, Button, Chip, Dialog, DialogContent, LinearProgress, Stack, Typography } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n, type ExcelRow } from 'wow-mes-frontend-shared';
import { i18nAdminApi } from '../services/adminApi';
import type { I18nEntry } from '../types/admin';

/**
 * 다국어 엑셀 — 내려받은 파일을 고쳐 다시 올리는 왕복(docs/frontend-design.md §5).
 * <ul>
 *   <li>칸: 코드·종류·KO·EN·JA·ZH·설명·사용. 종류는 이름(화면 문구·메뉴·메시지)이나 공통 코드 값 둘 다 읽는다.</li>
 *   <li>올릴 때 <b>빈 언어 칸은 그대로 둔다</b> — 엑셀로는 지우지 않는다(지우기는 화면에서). 칸이 아예 없어도 그대로.</li>
 *   <li>새 코드와 값이 바뀐 코드만 하나씩 저장한다(PUT /api/i18n/items/{code}, 설정 변경 이력에 남는다). 새 코드는 KO 가 있어야 한다.</li>
 * </ul>
 */
export const LANGS = ['KO', 'EN', 'JA', 'ZH'] as const;

export type Kind = { code: string; i18n: string; label: string };

const COL = { code: '코드', kind: '종류', description: '설명', use: '사용' }; // i18n-ignore 엑셀 칸 이름(왕복 파일 형식)

/** 표의 행 → 엑셀 행 */
export function toExcelRows(entries: I18nEntry[], kinds: Kind[], kindLabel: (k: Kind) => string): ExcelRow[] {
  return entries.map((e) => ({
    [COL.code]: e.code,
    [COL.kind]: kindLabel(kinds.find((k) => k.code === e.kind) ?? kinds[0]),
    ...Object.fromEntries(LANGS.map((l) => [l, e.values[l] ?? ''])),
    [COL.description]: e.description ?? '',
    [COL.use]: e.use === false ? 'N' : 'Y',
  }));
}

type PlanItem = { entry: I18nEntry; status: 'new' | 'changed' | 'same' };
type Plan = { items: PlanItem[]; errors: string[] };

const str = (v: unknown) => (v == null ? '' : String(v).trim());

/** 머리글을 대소문자·앞뒤 빈칸 없이 찾는다 */
function pick(row: ExcelRow, ...names: string[]): { has: boolean; value: string } {
  const want = names.map((n) => n.toLowerCase());
  const key = Object.keys(row).find((k) => want.includes(k.trim().toLowerCase()));
  return key === undefined ? { has: false, value: '' } : { has: true, value: str(row[key]) };
}

/** 엑셀 행 → 저장 계획(새로·바뀜·같음)과 오류 */
export function planUpload(rows: ExcelRow[], existing: I18nEntry[], kinds: Kind[], kindLabel: (k: Kind) => string): Plan {
  const byCode = new Map(existing.map((e) => [e.code, e]));
  const seen = new Set<string>();
  const items: PlanItem[] = [];
  const errors: string[] = [];
  rows.forEach((row, i) => {
    const line = i + 2; // 1행은 머리글
    const code = pick(row, COL.code, 'code').value;
    if (!code) return; // 빈 줄
    if (!/^[A-Za-z0-9_.-]{1,255}$/.test(code)) { errors.push(`${line}행 ${code}: 코드는 영숫자·_ . - 255자까지`); return; } // i18n-ignore 올림 검사 글
    if (seen.has(code)) { errors.push(`${line}행 ${code}: 파일 안에 같은 코드가 또 있습니다`); return; } // i18n-ignore 올림 검사 글
    seen.add(code);
    const old = byCode.get(code);
    const kindCell = pick(row, COL.kind, 'kind');
    let kind = old?.kind ?? kinds[0].code;
    if (kindCell.value) {
      const k = kinds.find((x) => x.code === kindCell.value || kindLabel(x) === kindCell.value || x.label === kindCell.value);
      if (!k) { errors.push(`${line}행 ${code}: 모르는 종류입니다 — ${kindCell.value}`); return; } // i18n-ignore 올림 검사 글
      kind = k.code;
    }
    const values: Record<string, string> = { ...(old?.values ?? {}) };
    for (const l of LANGS) {
      const cell = pick(row, l);
      if (cell.has && cell.value) values[l] = cell.value; // 빈 칸은 그대로
    }
    if (!values.KO) { errors.push(`${line}행 ${code}: KO 가 없습니다`); return; } // i18n-ignore 올림 검사 글
    const desc = pick(row, COL.description, 'description');
    const useCell = pick(row, COL.use, 'use');
    const use = useCell.has && useCell.value ? !['N', 'n', '0', 'false', '미사용'].includes(useCell.value) : (old?.use ?? true); // i18n-ignore 엑셀 값
    const entry: I18nEntry = { code, kind, parent: old?.parent, description: desc.has ? desc.value : old?.description, use, values };
    if (!old) { items.push({ entry, status: 'new' }); return; }
    const same = old.kind === entry.kind && (old.description ?? '') === (entry.description ?? '') && (old.use !== false) === entry.use
      && LANGS.every((l) => (old.values[l] ?? '') === (entry.values[l] ?? ''));
    items.push({ entry, status: same ? 'same' : 'changed' });
  });
  return { items, errors };
}

/** 올림 미리보기 → 반영(새로·바뀐 것만 하나씩 저장, 진행률) */
export function I18nUploadDialog({ plan, onClose, onDone }: {
  plan: Plan | null;
  onClose: () => void;
  onDone: (saved: number) => void;
}): ReactElement {
  const { t } = useI18n();
  const [done, setDone] = useState<number | null>(null);
  const [failures, setFailures] = useState<string[]>([]);
  const cancelRef = useRef(false);
  useEffect(() => { if (plan) { setDone(null); setFailures([]); cancelRef.current = false; } }, [plan]);
  const todo = plan?.items.filter((x) => x.status !== 'same') ?? [];
  const count = (s: PlanItem['status']) => plan?.items.filter((x) => x.status === s).length ?? 0;
  const running = done !== null && done < todo.length;

  const apply = async () => {
    const fails: string[] = [];
    setDone(0);
    let n = 0;
    for (const item of todo) {
      if (cancelRef.current) break;
      try {
        await i18nAdminApi.save(item.entry);
      } catch (e) {
        fails.push(`${item.entry.code}: ${extractApiError(e)}`);
      }
      n += 1;
      setDone(n);
    }
    setFailures(fails);
    onDone(n - fails.length);
  };

  const close = () => { cancelRef.current = true; onClose(); };
  return (
    <Dialog open={!!plan} onClose={running ? undefined : close} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={close}>{t('i18n.action.excelUpload', '엑셀 업로드')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={1.5}>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }} aria-label="upload-summary">
            <Chip color="success" variant="outlined" label={`${t('i18n.tc.i18n.upNew', '새로')} ${count('new')}`} />
            <Chip color="primary" variant="outlined" label={`${t('i18n.tc.i18n.upChanged', '바뀜')} ${count('changed')}`} />
            <Chip variant="outlined" label={`${t('i18n.tc.i18n.upSame', '같음')} ${count('same')}`} />
            <Chip color={plan?.errors.length ? 'error' : 'default'} variant="outlined" label={`${t('i18n.tc.i18n.upErrors', '오류')} ${plan?.errors.length ?? 0}`} />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            {t('i18n.tc.i18n.upHelp', '새 코드와 바뀐 코드만 저장합니다. 빈 언어 칸은 지우지 않고 그대로 둡니다. 오류 줄은 건너뜁니다.')}
          </Typography>
          {!!plan?.errors.length && (
            <Alert severity="warning" sx={{ maxHeight: 160, overflow: 'auto', whiteSpace: 'pre-line' }}>
              {plan.errors.slice(0, 50).join('\n')}{plan.errors.length > 50 ? `\n… ${plan.errors.length - 50}` : ''}
            </Alert>
          )}
          {done !== null && (
            <Box>
              <LinearProgress variant="determinate" value={todo.length ? (done / todo.length) * 100 : 100} />
              <Typography variant="caption" color="text.secondary">{done} / {todo.length}</Typography>
            </Box>
          )}
          {!!failures.length && (
            <Alert severity="error" sx={{ maxHeight: 160, overflow: 'auto', whiteSpace: 'pre-line' }}>{failures.join('\n')}</Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={close}>
        <Button variant="contained" disabled={running || done !== null || todo.length === 0} onClick={() => { void apply(); }}>
          {t('i18n.tc.i18n.upApply', '반영')} ({todo.length})
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
