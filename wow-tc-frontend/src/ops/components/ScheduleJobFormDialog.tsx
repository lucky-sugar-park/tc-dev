import { useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Box, Button, Checkbox, Dialog, DialogContent, FormControlLabel, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import type { InterfaceInfo } from '../../plc/types/plc';
import type { JobDetail, JobKind, ScheduleJob } from '../types/ops';
import { JobPropertiesEditor, presetProperties, propertyErrors, withSpecRows } from './JobPropertiesEditor';

/** 종류 이름(목록·선택) — 백엔드 ScheduleJobKind */
export function kindLabel(t: (k: string, d: string) => string, kind?: string | null): string {
  if (kind === 'PLC') return t('i18n.tc.schedule.kind.plc', 'PLC 요청');
  if (kind === 'CLEANUP') return t('i18n.tc.schedule.kind.cleanup', '오래된 기록 정리');
  return kind ?? '';
}

const EMPTY: ScheduleJob = {
  name: '', use: true, jobClassName: '', triggerType: 'cron', cronExpression: '0 0/1 * * * ?', startDelay: 0, repeatInterval: 60000,
  jobDetails: [], description: '',
};

/**
 * 스케줄 Job 등록·수정 — 종류(서버의 Job 종류 목록)를 고르면 그 종류의 속성이 표에 미리 들어간다(docs/schedule-jobs.md).
 * 실행 주기는 cron 또는 시작 지연 + 반복 간격(ms). 이름·종류는 등록 뒤 바꿀 수 없다. 저장하면 화면이 (다시) 예약한다.
 */
export function ScheduleJobFormDialog({ open, initial, kinds, interfaces, onClose, onSubmit, error }: {
  open: boolean;
  initial?: ScheduleJob;
  kinds: JobKind[];
  interfaces: InterfaceInfo[];
  onClose: () => void;
  onSubmit: (job: ScheduleJob) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<ScheduleJob>(EMPTY);
  const [kindName, setKindName] = useState('');
  const [rows, setRows] = useState<JobDetail[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [showPropErrors, setShowPropErrors] = useState(false);
  const kind = kinds.find((k) => k.kind === kindName);

  useEffect(() => {
    if (!open) return;
    const k = initial ? kinds.find((x) => x.kind === initial.kind) : kinds[0];
    setV(initial ? { ...EMPTY, ...initial } : { ...EMPTY, jobClassName: k?.jobClassName ?? '' });
    setKindName(k?.kind ?? '');
    setRows(initial ? withSpecRows(initial.jobDetails ?? [], k) : presetProperties(k));
    setErrors({});
    setShowPropErrors(false);
  }, [open, initial, kinds]);
  const set = (patch: Partial<ScheduleJob>) => setV((s) => ({ ...s, ...patch }));
  const propErrors = useMemo(() => propertyErrors(rows, kind, t), [rows, kind, t]);

  const changeKind = (name: string) => {
    const k = kinds.find((x) => x.kind === name);
    setKindName(name);
    set({ jobClassName: k?.jobClassName ?? '' });
    setRows(presetProperties(k));
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(v.name)) errs.name = t('i18n.tc.connector.nameRule', '영숫자·_ . - 100자까지');
    if (!kind) errs.kind = t('i18n.tc.common.required', '필수입니다');
    if (v.triggerType === 'cron' && !v.cronExpression?.trim()) errs.cron = t('i18n.tc.common.required', '필수입니다');
    if (v.triggerType === 'simple' && !(Number(v.repeatInterval) >= 1000)) errs.repeat = t('i18n.tc.schedule.repeatRule', '1000ms 이상');
    setErrors(errs);
    setShowPropErrors(true);
    if (Object.keys(errs).length || Object.keys(propErrors).length) return;
    onSubmit({ ...v, jobDetails: rows.map((r) => ({ ...r, entryKey: r.entryKey.trim() })) });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.schedule.edit', '스케줄 Job 수정') : t('i18n.tc.schedule.add', '스케줄 Job 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Alert severity="info">{t('i18n.tc.schedule.saveHelp', '사용이면 저장하면서 이 주기로 (다시) 예약한다. 쓰지 않음으로 저장하면 예약을 취소한다.')}</Alert>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
              error={!!errors.name} helperText={errors.name} slotProps={{ input: { readOnly: edit } }} />
            <TextField select label={t('i18n.tc.schedule.kind', '종류')} value={kindName} onChange={(e) => changeKind(e.target.value)} disabled={edit}
              error={!!errors.kind} helperText={errors.kind}>
              {kinds.map((k) => <MenuItem key={k.kind} value={k.kind}>{kindLabel(t, k.kind)}</MenuItem>)}
            </TextField>
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 2 }}>
            <TextField select label={t('i18n.tc.schedule.trigger', '주기')} value={v.triggerType} onChange={(e) => set({ triggerType: e.target.value as 'cron' | 'simple' })}>
              <MenuItem value="cron">cron</MenuItem>
              <MenuItem value="simple">{t('i18n.tc.schedule.simple', '간격')}</MenuItem>
            </TextField>
            {v.triggerType === 'cron' ? (
              <TextField label="cron" value={v.cronExpression ?? ''} onChange={(e) => set({ cronExpression: e.target.value })}
                error={!!errors.cron} helperText={errors.cron ?? t('i18n.tc.schedule.cronHelp', '초 분 시 일 월 요일 — 예: 0 0/1 * * * ? (1분마다)')} />
            ) : (
              <Stack direction="row" spacing={1}>
                <TextField label={t('i18n.tc.schedule.startDelay', '시작 지연(ms)')} type="number" value={v.startDelay ?? 0}
                  onChange={(e) => set({ startDelay: Number(e.target.value) })} />
                <TextField label={t('i18n.tc.schedule.repeat', '반복 간격(ms)')} type="number" value={v.repeatInterval ?? ''}
                  onChange={(e) => set({ repeatInterval: Number(e.target.value) })} error={!!errors.repeat} helperText={errors.repeat} />
              </Stack>
            )}
          </Box>
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('i18n.tc.schedule.properties', '속성')}</Typography>
            <JobPropertiesEditor rows={rows} onChange={setRows} kind={kind} interfaces={interfaces} errors={showPropErrors ? propErrors : {}} />
          </Box>
          <FormControlLabel control={<Checkbox checked={v.use} onChange={(e) => set({ use: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
