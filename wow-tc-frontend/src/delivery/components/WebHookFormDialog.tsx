import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Button, Dialog, DialogContent, Stack, TextField } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { webhookApi } from '../services/deliveryApi';
import type { WebHookInfo } from '../types/delivery';

const EMPTY: WebHookInfo = { name: '', url: '', sampleData: '{\n  "data": {}\n}', description: '' };

const validJson = (s?: string): boolean => {
  if (!s || !s.trim()) return true;
  try { JSON.parse(s); return true; } catch { return false; }
};

/**
 * WebHook 등록·수정 + 보내 보기 (docs/frontend-design.md §5). 보내 보기는 tc 가 그 주소로 시험 데이터를 POST 한다(허용 목록
 * TC_DELIVERY_ALLOWED_URL_PREFIXES 안의 주소만 실제 전달된다). 옛 화면의 표 안 편집 대신 대화상자.
 */
export function WebHookFormDialog({ open, initial, canTest, onClose, onSubmit, error }: {
  open: boolean;
  initial?: WebHookInfo;
  canTest: boolean;
  onClose: () => void;
  onSubmit: (h: WebHookInfo) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<WebHookInfo>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [check, setCheck] = useState<{ ok: boolean; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (open) { setV(initial ? { ...EMPTY, ...initial } : EMPTY); setErrors({}); setCheck(null); } }, [open, initial]);
  const set = (patch: Partial<WebHookInfo>) => setV((s) => ({ ...s, ...patch }));

  const validate = (): boolean => {
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(v.name)) errs.name = t('i18n.tc.connector.nameRule', '영숫자·_ . - 100자까지');
    if (!/^https?:\/\/\S+$/.test(v.url)) errs.url = t('i18n.tc.webhook.urlRule', 'http(s):// 로 시작하는 주소');
    if (!validJson(v.sampleData)) errs.sampleData = t('i18n.tc.interfaceTest.badJson', 'JSON 객체가 아닙니다');
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (validate()) onSubmit(v);
  };

  const test = async () => {
    if (!validate()) return;
    setBusy(true);
    try {
      const r = await webhookApi.check(v.url, v.sampleData ?? '');
      setCheck({ ok: r.result === 'OK', text: r.result === 'OK' ? t('i18n.tc.webhook.checkOk', '보냈습니다(2xx 응답)') : (r.cause ?? r.result) });
    } catch (e) {
      setCheck({ ok: false, text: extractApiError(e) });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.webhook.edit', 'WebHook 수정') : t('i18n.tc.webhook.add', 'WebHook 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
            error={!!errors.name} helperText={errors.name} slotProps={{ input: { readOnly: edit } }} />
          <TextField label="URL" required value={v.url} onChange={(e) => set({ url: e.target.value })} placeholder="https://mes.example.com/hook"
            error={!!errors.url} helperText={errors.url} />
          <TextField label={t('i18n.tc.webhook.sample', '시험 데이터(JSON)')} multiline minRows={5} value={v.sampleData ?? ''}
            onChange={(e) => set({ sampleData: e.target.value })} error={!!errors.sampleData} helperText={errors.sampleData}
            slotProps={{ htmlInput: { style: { fontFamily: 'monospace', fontSize: 13 } } }} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
          {check && <Alert severity={check.ok ? 'success' : 'error'}>{check.text}</Alert>}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}>
        {canTest && <Button onClick={() => { void test(); }} disabled={busy}>{t('i18n.tc.webhook.check', '보내 보기')}</Button>}
        <Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button>
      </DialogFooter>
    </Dialog>
  );
}
