import { useEffect, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Autocomplete, Box, Button, Checkbox, Dialog, DialogContent, FormControlLabel, MenuItem, Stack, TextField } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import type { DeliverOn, DeliveryRule, DeliveryType } from '../types/delivery';

const EMPTY: DeliveryRule = { interfaceName: '', deliveryType: 'WEBHOOK', deliverOn: 'ALL', onChangeOnly: false, use: true };

/**
 * 결과 전달 정의 등록·수정 (docs/frontend-design.md §5). 인터페이스에 정의가 하나라도 있으면 그 정의대로만 보낸다(없으면 옛 방식 —
 * 요청의 callback + 응답 인터페이스의 WebHook). 대상은 유형마다 다르다 — WEBHOOK 은 WebHook 이름, DB_TABLE 은 DB 연결 이름(tc 자체는 tc)과
 * 테이블, CALLBACK_URL 은 요청이 준 주소라 대상이 없다.
 */
export function DeliveryRuleFormDialog({ open, initial, interfaces, webhooks, dbConnections, onClose, onSubmit, error }: {
  open: boolean;
  initial?: DeliveryRule;
  interfaces: string[];
  webhooks: string[];
  dbConnections: string[];
  onClose: () => void;
  onSubmit: (rule: DeliveryRule) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<DeliveryRule>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => { if (open) { setV(initial ? { ...EMPTY, ...initial } : EMPTY); setErrors({}); } }, [open, initial]);
  const set = (patch: Partial<DeliveryRule>) => setV((s) => ({ ...s, ...patch }));

  const targets = v.deliveryType === 'WEBHOOK' ? webhooks : v.deliveryType === 'DB_TABLE' ? ['tc', ...dbConnections] : [];

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!v.interfaceName) errs.interfaceName = t('i18n.tc.common.required', '필수입니다');
    if (v.deliveryType !== 'CALLBACK_URL' && !v.targetName) errs.targetName = t('i18n.tc.common.required', '필수입니다');
    if (v.deliveryType === 'DB_TABLE' && !v.tableName) errs.tableName = t('i18n.tc.common.required', '필수입니다');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit({
      ...v,
      id: v.id || undefined,
      targetName: v.deliveryType === 'CALLBACK_URL' ? null : v.targetName,
      tableName: v.deliveryType === 'DB_TABLE' ? v.tableName : null,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.delivery.edit', '전달 정의 수정') : t('i18n.tc.delivery.add', '전달 정의 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Autocomplete options={interfaces} value={v.interfaceName || null} disabled={edit}
            onChange={(_e, val) => set({ interfaceName: val ?? '' })}
            renderInput={(p) => <TextField {...p} required label={t('i18n.tc.delivery.interface', '인터페이스(opcode)')}
              error={!!errors.interfaceName} helperText={errors.interfaceName} />} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField select label={t('i18n.tc.delivery.type', '유형')} value={v.deliveryType}
              onChange={(e) => set({ deliveryType: e.target.value as DeliveryType, targetName: null })}>
              <MenuItem value="WEBHOOK">WEBHOOK</MenuItem>
              <MenuItem value="DB_TABLE">DB_TABLE</MenuItem>
              <MenuItem value="CALLBACK_URL">CALLBACK_URL</MenuItem>
            </TextField>
            <TextField select label={t('i18n.tc.delivery.deliverOn', '보낼 때')} value={v.deliverOn}
              onChange={(e) => set({ deliverOn: e.target.value as DeliverOn })}>
              <MenuItem value="ALL">{t('i18n.tc.delivery.on.all', '늘')}</MenuItem>
              <MenuItem value="SUCCESS">{t('i18n.tc.delivery.on.success', '성공일 때만')}</MenuItem>
              <MenuItem value="FAILURE">{t('i18n.tc.delivery.on.failure', '실패일 때만')}</MenuItem>
            </TextField>
          </Box>
          {v.deliveryType !== 'CALLBACK_URL' ? (
            <TextField select label={v.deliveryType === 'WEBHOOK' ? 'WebHook' : t('i18n.tc.delivery.dbConnection', 'DB 연결')}
              value={v.targetName ?? ''} onChange={(e) => set({ targetName: e.target.value })} required
              error={!!errors.targetName} helperText={errors.targetName}>
              {targets.map((n) => <MenuItem key={n} value={n}>{n}</MenuItem>)}
            </TextField>
          ) : (
            <Alert severity="info" sx={{ py: 0 }}>{t('i18n.tc.delivery.callbackHelp', '요청의 pushUrl 로 보낸다 — 허용 목록(TC_DELIVERY_ALLOWED_URL_PREFIXES)·API 클라이언트의 URL 접두어 안에서만.')}</Alert>
          )}
          {v.deliveryType === 'DB_TABLE' && (
            <TextField label={t('i18n.tc.delivery.table', '테이블')} required value={v.tableName ?? ''} onChange={(e) => set({ tableName: e.target.value })}
              error={!!errors.tableName} helperText={errors.tableName ?? t('i18n.tc.delivery.tableHelp', '표준 칼럼이 있어야 한다(docs/db-table-delivery.md). 스키마는 schema.table')} />
          )}
          <Stack direction="row" sx={{ flexWrap: 'wrap', columnGap: 2 }}>
            <FormControlLabel control={<Checkbox checked={v.use} onChange={(e) => set({ use: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
            <FormControlLabel control={<Checkbox checked={v.onChangeOnly} onChange={(e) => set({ onChangeOnly: e.target.checked })} />}
              label={t('i18n.tc.delivery.onChangeOnly', '값이 바뀌었을 때만(스케줄 폴링)')} />
          </Stack>
          <TextField label={t('i18n.tc.delivery.id', '정의 ID')} value={v.id ?? ''} onChange={(e) => set({ id: e.target.value })}
            helperText={edit ? undefined : t('i18n.tc.delivery.idHelp', '비우면 인터페이스.유형.대상 으로 만든다')} slotProps={{ input: { readOnly: edit } }} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
