import { useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Autocomplete, Box, Button, Checkbox, Dialog, DialogContent, Divider, FormControlLabel, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import { PointsEditor, emptyPoint } from './PointsEditor';
import type { InterfaceInfo, PlcConnectorInfo, PointInfo, ProtocolDescriptor } from '../types/plc';

/**
 * POINTS 인터페이스 등록·수정 (docs/frontend-design.md §5) — 점(이름·주소·형)으로 정의한다. 응답 인터페이스·헤더 바이트·데이터 길이는
 * 없다(옛 4단계 마법사 대신). 점 주소·형은 커넥터의 프로토콜로 서버가 검증한다. 쓰기 인터페이스는 다시 읽기(readBack)를 고를 수 있다.
 */
export function InterfaceFormDialog({ open, initial, connectors, protocols, webhooks, onClose, onSubmit, error }: {
  open: boolean;
  initial?: InterfaceInfo;
  connectors: PlcConnectorInfo[];
  protocols: ProtocolDescriptor[];
  webhooks: string[];
  onClose: () => void;
  onSubmit: (info: InterfaceInfo) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<InterfaceInfo | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setV(initial ? { ...initial, points: initial.points ?? [] } : {
      name: '', plcName: connectors[0]?.name, interfaceType: 'READ', mode: 'POINTS', use: true, sync: true, reply: true,
      readBack: false, webHookNameList: [], description: '', points: [emptyPoint()],
    });
    setNameError(null);
  }, [open, initial, connectors]);

  const protocol = useMemo(() => {
    const c = connectors.find((x) => x.name === v?.plcName);
    return protocols.find((p) => p.protocol === (c?.protocol ?? 'MELSEC_MC'));
  }, [connectors, protocols, v?.plcName]);

  if (!v) return <Dialog open={false} />;
  const set = (patch: Partial<InterfaceInfo>) => setV({ ...v, ...patch });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!/^[A-Za-z0-9_.-]{1,200}$/.test(v.name)) { setNameError(t('i18n.tc.interface.nameRule', '영숫자·_ . - (opcode 로 쓰인다)')); return; }
    const points: PointInfo[] = (v.points ?? []).filter((p) => p.name || p.address).map((p, i) => ({ ...p, order: i }));
    onSubmit({ ...v, mode: 'POINTS', points, readBack: v.interfaceType === 'WRITE' ? !!v.readBack : false });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>
        {edit ? t('i18n.tc.interface.edit', '인터페이스 수정') : t('i18n.tc.interface.add', '인터페이스 등록')}
      </DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '2fr 1fr 1fr' }, gap: 2 }}>
            <TextField label={t('i18n.tc.interface.name', '이름(opcode)')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
              error={!!nameError} helperText={nameError} slotProps={{ input: { readOnly: edit } }} />
            <TextField select label={t('i18n.tc.interface.plc', 'PLC 커넥터')} required value={v.plcName ?? ''} onChange={(e) => set({ plcName: e.target.value })}>
              {connectors.map((c) => <MenuItem key={c.name} value={c.name}>{c.name} ({c.protocol})</MenuItem>)}
            </TextField>
            <TextField select label={t('i18n.tc.interface.type', '형')} value={v.interfaceType} onChange={(e) => set({ interfaceType: e.target.value as 'READ' | 'WRITE' })}
              slotProps={{ input: { readOnly: edit } }}>
              <MenuItem value="READ">READ</MenuItem>
              <MenuItem value="WRITE">WRITE</MenuItem>
            </TextField>
          </Box>
          <Stack direction="row" sx={{ flexWrap: 'wrap', columnGap: 2 }}>
            <FormControlLabel control={<Checkbox checked={!!v.use} onChange={(e) => set({ use: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
            <FormControlLabel control={<Checkbox checked={!!v.sync} onChange={(e) => set({ sync: e.target.checked })} />} label={t('i18n.tc.interface.sync', '동기')} />
            <FormControlLabel control={<Checkbox checked={!!v.reply} onChange={(e) => set({ reply: e.target.checked })} />} label={t('i18n.tc.interface.reply', '응답')} />
            {v.interfaceType === 'WRITE' && (
              <FormControlLabel control={<Checkbox checked={!!v.readBack} onChange={(e) => set({ readBack: e.target.checked })} />}
                label={t('i18n.tc.interface.readBack', '쓴 뒤 다시 읽기')} />
            )}
          </Stack>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' }, gap: 2 }}>
            <Autocomplete multiple options={webhooks} value={v.webHookNameList ?? []} onChange={(_e, val) => set({ webHookNameList: val })}
              renderInput={(p) => <TextField {...p} label="WebHook" helperText={t('i18n.tc.interface.webhookHelp', '응답을 보낼 WebHook(전달 정의는 전달 화면에서)')} />} />
            <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
          </Box>
          <Divider />
          <Typography variant="subtitle2">
            {t('i18n.tc.interface.points', '점')} — {protocol?.label ?? ''}
            {v.interfaceType === 'WRITE' ? ` · ${t('i18n.tc.interface.writeHelp', '요청 data 의 점 이름 값을 쓴다')}` : ` · ${t('i18n.tc.interface.readHelp', '읽은 값을 data 의 점 이름으로 돌려준다')}`}
          </Typography>
          <PointsEditor points={v.points ?? []} onChange={(points) => set({ points })} protocol={protocol} writeInterface={v.interfaceType === 'WRITE'} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
