import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Autocomplete, Box, Button, Checkbox, Dialog, DialogContent, FormControlLabel, IconButton, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import type { JobDetail, ScheduleJob } from '../types/ops';

const PKG = 'com.wow.be.tc.schedule.jobs.';

/** Job 종류 — 클래스 이름을 적지 않게(옛 화면) 고른다. 기타는 클래스·값을 직접 */
export const JOB_KINDS = [
  { kind: 'PLC', className: `${PKG}PlcGeneralScheduleJob`, label: 'PLC 요청', labelCode: 'i18n.tc.schedule.kind.plc' },
  { kind: 'AUDIT_CLEAN', className: `${PKG}DataTableFlashJob`, label: '송수신 기록 정리', labelCode: 'i18n.tc.schedule.kind.auditClean' },
  { kind: 'OTHER', className: '', label: '기타(클래스 직접)', labelCode: 'i18n.tc.schedule.kind.other' },
] as const;

type Kind = typeof JOB_KINDS[number]['kind'];

export function kindOf(job: ScheduleJob): Kind {
  const hit = JOB_KINDS.find((k) => k.className && job.jobClassName?.endsWith(k.className.slice(PKG.length)));
  if (hit?.kind === 'AUDIT_CLEAN' && job.jobDetails?.find((d) => d.entryKey === 'flashByType')?.entryValue !== 'SERVICE_BEAN') return 'OTHER';
  return hit?.kind ?? 'OTHER';
}

const detail = (entryKey: string, entryValue: string): JobDetail => ({ entryKey, entryValue, entryValueType: 'string', oper: 'C' });

const EMPTY: ScheduleJob = {
  name: '', use: true, jobClassName: JOB_KINDS[0].className, groupName: 'TC', schedulerName: 'TcScheduler',
  triggerType: 'cron', cronExpression: '0 0/1 * * * ?', startDelay: 1000, repeatInterval: 60000, jobDetails: [], description: '',
};

/**
 * 스케줄 Job 등록·수정 (docs/frontend-design.md §5) — 종류(PLC 요청·송수신 기록 정리·기타)를 고르면 필요한 값만 묻는다. 실행 주기는 cron 또는
 * 시작 지연 + 반복 간격(ms). 저장하면 화면이 (다시) 예약한다.
 */
export function ScheduleJobFormDialog({ open, initial, interfaces, onClose, onSubmit, error }: {
  open: boolean;
  initial?: ScheduleJob;
  interfaces: string[];
  onClose: () => void;
  onSubmit: (job: ScheduleJob) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<ScheduleJob>(EMPTY);
  const [kind, setKind] = useState<Kind>('PLC');
  const [interfaceName, setInterfaceName] = useState('');
  const [savingDays, setSavingDays] = useState('30');
  const [others, setOthers] = useState<JobDetail[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    const job = initial ? { ...EMPTY, ...initial } : EMPTY;
    const k = initial ? kindOf(initial) : 'PLC';
    const d = (key: string) => job.jobDetails?.find((x) => x.entryKey === key)?.entryValue ?? '';
    setV(job); setKind(k); setInterfaceName(d('interfaceName')); setSavingDays(d('savingDays') || '30');
    setOthers(k === 'OTHER' ? (job.jobDetails ?? []).map((x) => ({ ...x })) : []);
    setErrors({});
  }, [open, initial]);
  const set = (patch: Partial<ScheduleJob>) => setV((s) => ({ ...s, ...patch }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(v.name)) errs.name = t('i18n.tc.connector.nameRule', '영숫자·_ . - 100자까지');
    if (kind === 'PLC' && !interfaceName) errs.interfaceName = t('i18n.tc.common.required', '필수입니다');
    if (kind === 'AUDIT_CLEAN' && !(Number(savingDays) >= 1)) errs.savingDays = t('i18n.tc.schedule.daysRule', '1일 이상');
    if (kind === 'OTHER' && !v.jobClassName) errs.jobClassName = t('i18n.tc.common.required', '필수입니다');
    if (v.triggerType === 'cron' && !v.cronExpression?.trim()) errs.cron = t('i18n.tc.common.required', '필수입니다');
    if (v.triggerType === 'simple' && !(Number(v.repeatInterval) >= 1000)) errs.repeat = t('i18n.tc.schedule.repeatRule', '1000ms 이상');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    const jobClassName = kind === 'OTHER' ? v.jobClassName : JOB_KINDS.find((x) => x.kind === kind)!.className;
    const jobDetails = kind === 'PLC' ? [detail('interfaceName', interfaceName)]
      : kind === 'AUDIT_CLEAN' ? [detail('flashByType', 'SERVICE_BEAN'), detail('serviceBeanName', 'auditMessageServiceBean'),
        detail('methodName', 'flashBySavingPeriod'), detail('targetName', 'EXCHANGED_MESSAGE_AUDIT'), detail('savingDays', String(Number(savingDays)))]
      : others.filter((x) => x.entryKey).map((x) => ({ ...x, oper: 'C' }));
    onSubmit({ ...v, jobClassName, jobDetails });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.schedule.edit', '스케줄 Job 수정') : t('i18n.tc.schedule.add', '스케줄 Job 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Alert severity="info">{t('i18n.tc.schedule.saveHelp', '사용이면 저장하면서 이 주기로 (다시) 예약한다. 쓰지 않음으로 저장하면 예약을 취소한다.')}</Alert>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
              error={!!errors.name} helperText={errors.name} slotProps={{ input: { readOnly: edit } }} />
            <TextField select label={t('i18n.tc.schedule.kind', '종류')} value={kind} onChange={(e) => setKind(e.target.value as Kind)} disabled={edit}>
              {JOB_KINDS.map((k) => <MenuItem key={k.kind} value={k.kind}>{t(k.labelCode, k.label)}</MenuItem>)}
            </TextField>
          </Box>
          {kind === 'PLC' && (
            <Autocomplete options={interfaces} value={interfaceName || null} onChange={(_e, val) => setInterfaceName(val ?? '')}
              renderInput={(p) => <TextField {...p} required label={t('i18n.tc.delivery.interface', '인터페이스(opcode)')}
                error={!!errors.interfaceName} helperText={errors.interfaceName ?? t('i18n.tc.schedule.plcHelp', '주기마다 이 인터페이스로 PLC 요청 — 결과는 전달 정의대로')} />} />
          )}
          {kind === 'AUDIT_CLEAN' && (
            <TextField label={t('i18n.tc.schedule.savingDays', '보관 일수')} type="number" value={savingDays} onChange={(e) => setSavingDays(e.target.value)}
              error={!!errors.savingDays} helperText={errors.savingDays ?? t('i18n.tc.schedule.savingHelp', '이보다 오래된 송수신 기록을 지운다')} />
          )}
          {kind === 'OTHER' && (
            <>
              <TextField label={t('i18n.tc.schedule.className', 'Job 클래스')} value={v.jobClassName} onChange={(e) => set({ jobClassName: e.target.value })}
                error={!!errors.jobClassName} helperText={errors.jobClassName} />
              <Typography variant="caption">{t('i18n.tc.schedule.values', 'Job 값')}</Typography>
              {others.map((d, i) => (
                <Stack key={i} direction="row" spacing={1}>
                  <TextField size="small" label="key" value={d.entryKey} onChange={(e) => setOthers(others.map((x, k) => (k === i ? { ...x, entryKey: e.target.value } : x)))} />
                  <TextField size="small" label="value" sx={{ flex: 1 }} value={d.entryValue} onChange={(e) => setOthers(others.map((x, k) => (k === i ? { ...x, entryValue: e.target.value } : x)))} />
                  <IconButton size="small" onClick={() => setOthers(others.filter((_, k) => k !== i))}><DeleteOutlineIcon fontSize="small" /></IconButton>
                </Stack>
              ))}
              <Box><IconButton size="small" color="primary" onClick={() => setOthers([...others, detail('', '')])}><AddIcon fontSize="small" /></IconButton></Box>
            </>
          )}
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
          <FormControlLabel control={<Checkbox checked={v.use} onChange={(e) => set({ use: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
