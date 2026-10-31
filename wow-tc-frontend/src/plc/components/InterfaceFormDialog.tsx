import { useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Box, Button, Checkbox, Dialog, DialogContent, FormControlLabel, MenuItem, Stack, Tab, Tabs, TextField, Typography,
} from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import { PointsEditor, emptyPoint } from './PointsEditor';
import { RequestPlanView } from './RequestPlanView';
import { SettingsFields, settingText, settingsFromText } from './SettingsFields';
import type { InterfaceInfo, PlcConnectorInfo, PointInfo, ProtocolDescriptor } from '../types/plc';

type TabKey = 'basic' | 'settings' | 'points' | 'plan';

/**
 * 인터페이스 등록·수정 (docs/frontend-design.md §5, docs/interfaces.md) — 탭: 기본 · 프로토콜 설정(인터페이스 settings,
 * 비우면 커넥터 값) · 점 · 요청 미리보기. 헤더 바이트·데이터 길이는 없다(옛 4단계 마법사 대신) — 프레임·국 번호는 커넥터 설정과 이 인터페이스의
 * settings 에서, 명령·주소·길이는 드라이버가 점에서 만든다. 검증은 서버(커넥터의 프로토콜 드라이버).
 */
export function InterfaceFormDialog({ open, initial, connectors, protocols, onClose, onSubmit, error }: {
  open: boolean;
  initial?: InterfaceInfo;
  connectors: PlcConnectorInfo[];
  protocols: ProtocolDescriptor[];
  onClose: () => void;
  onSubmit: (info: InterfaceInfo) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<InterfaceInfo | null>(null);
  const [settingValues, setSettingValues] = useState<Record<string, string>>({});
  const [nameError, setNameError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabKey>('basic');

  const connector = connectors.find((x) => x.name === v?.plcName);
  const protocol = useMemo(
    () => protocols.find((p) => p.protocol === (connector?.protocol ?? 'MELSEC_MC')),
    [protocols, connector?.protocol],
  );
  // 같은 배열을 쓴다 — 아래 effect 가 이것이 바뀔 때만(프로토콜이 바뀔 때만) 칸을 다시 채운다
  const schema = useMemo(() => protocol?.interfaceSettings ?? [], [protocol]);

  useEffect(() => {
    if (!open) return;
    setV(initial ? { ...initial, points: initial.points ?? [] } : {
      name: '', plcName: connectors[0]?.name, interfaceType: 'READ', use: true,
      readBack: false, description: '', points: [emptyPoint()], settings: {},
    });
    setNameError(null);
    setTab('basic');
  }, [open, initial, connectors]);

  // 설정 칸 글자 — 열 때와 프로토콜이 바뀔 때(다른 프로토콜의 키는 버린다)
  useEffect(() => {
    if (!open) return;
    const from = initial?.settings ?? {};
    setSettingValues(Object.fromEntries(schema.map((s) => [s.key, settingText(from[s.key], s)])));
  }, [open, initial, schema]);

  if (!v) return <Dialog open={false} />;
  const set = (patch: Partial<InterfaceInfo>) => setV({ ...v, ...patch });
  const points: PointInfo[] = (v.points ?? []).filter((p) => p.name || p.address).map((p, i) => ({ ...p, order: i }));
  const draft: InterfaceInfo = { ...v, points, settings: settingsFromText(schema, settingValues) };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!/^[A-Za-z0-9_.-]{1,200}$/.test(v.name)) {
      setNameError(t('i18n.tc.interface.nameRule', '영숫자·_ . - (opcode 로 쓰인다)'));
      setTab('basic');
      return;
    }
    onSubmit({ ...draft, readBack: v.interfaceType === 'WRITE' ? !!v.readBack : false });
  };

  /** 비운 칸이 쓰는 값 — 커넥터에 적은 값, 없으면 서버 기본값 */
  const inherited = (key: string) => {
    const s = schema.find((x) => x.key === key);
    const fromConnector = connector?.settings?.[key];
    if (fromConnector !== undefined && fromConnector !== null && fromConnector !== '') {
      return `${t('i18n.tc.interface.connectorValue', '커넥터 값')} ${settingText(fromConnector, s)}`;
    }
    return s?.defaultValue != null ? `${t('i18n.tc.connector.default', '기본')} ${settingText(s.defaultValue, s)}` : t('i18n.tc.interface.connectorValue', '커넥터 값');
  };
  const overridden = Object.keys(draft.settings ?? {}).length;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>
        {edit ? t('i18n.tc.interface.edit', '인터페이스 수정') : t('i18n.tc.interface.add', '인터페이스 등록')}
      </DialogTitleClose>
      <Tabs value={tab} onChange={(_e, k: TabKey) => setTab(k)} sx={{ px: 2, borderBottom: 1, borderColor: 'divider', minHeight: 40 }}>
        <Tab value="basic" label={t('i18n.tc.interface.tabBasic', '기본')} sx={{ minHeight: 40 }} />
        <Tab value="settings" sx={{ minHeight: 40 }}
          label={`${t('i18n.tc.connector.settings', '프로토콜 설정')}${overridden ? ` (${overridden})` : ''}`} />
        <Tab value="points" label={`${t('i18n.tc.interface.points', '점')} (${points.length})`} sx={{ minHeight: 40 }} />
        <Tab value="plan" label={t('i18n.tc.plan.title', '요청 미리보기')} sx={{ minHeight: 40 }} />
      </Tabs>
      <DialogContent sx={{ minHeight: 360 }}>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {tab === 'basic' && (
            <>
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '2fr 1fr 1fr' }, gap: 2 }}>
                <TextField label={t('i18n.tc.interface.name', '이름(opcode)')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
                  error={!!nameError} helperText={nameError} slotProps={{ input: { readOnly: edit } }} />
                <TextField select label={t('i18n.tc.interface.plc', 'PLC 커넥터')} required value={v.plcName ?? ''} onChange={(e) => set({ plcName: e.target.value })}>
                  {connectors.map((c) => <MenuItem key={c.name} value={c.name}>{c.name} ({c.protocol})</MenuItem>)}
                </TextField>
                <TextField select label={t('i18n.tc.interface.ifType', '타입')} value={v.interfaceType} onChange={(e) => set({ interfaceType: e.target.value as 'READ' | 'WRITE' })}
                  slotProps={{ input: { readOnly: edit } }}>
                  <MenuItem value="READ">READ</MenuItem>
                  <MenuItem value="WRITE">WRITE</MenuItem>
                </TextField>
              </Box>
              <Stack direction="row" sx={{ flexWrap: 'wrap', columnGap: 2 }}>
                <FormControlLabel control={<Checkbox checked={!!v.use} onChange={(e) => set({ use: e.target.checked })} />} label={t('i18n.tc.interface.use', '사용')} />
                {v.interfaceType === 'WRITE' && (
                  <FormControlLabel control={<Checkbox checked={!!v.readBack} onChange={(e) => set({ readBack: e.target.checked })} />}
                    label={t('i18n.tc.interface.readBack', '쓴 뒤 다시 읽기')} />
                )}
              </Stack>
              <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
            </>
          )}
          {tab === 'settings' && (
            <>
              <Typography variant="body2" color="text.secondary">
                {protocol?.label} — {t('i18n.tc.interface.settingsHelp', '이 인터페이스에만 쓸 값. 비우면 커넥터 값을 씁니다(중계할 국 번호, 게이트웨이 뒤 장치 번호, 쓰기 함수, 묶기 등)')}
              </Typography>
              <SettingsFields schema={schema} values={settingValues} onChange={setSettingValues} fallbackLabel={(s) => inherited(s.key)} />
            </>
          )}
          {tab === 'points' && (
            <>
              <Typography variant="subtitle2">
                {t('i18n.tc.interface.points', '점')} — {protocol?.label ?? ''}
                {v.interfaceType === 'WRITE' ? ` · ${t('i18n.tc.interface.writeHelp', '요청 data 의 점 이름 값을 쓴다')}` : ` · ${t('i18n.tc.interface.readHelp', '읽은 값을 data 의 점 이름으로 돌려준다')}`}
              </Typography>
              <PointsEditor points={v.points ?? []} onChange={(pts) => set({ points: pts })} protocol={protocol} writeInterface={v.interfaceType === 'WRITE'} />
            </>
          )}
          {/* 저장 전 정의(설정·점 포함)로 — 버튼을 눌러야 부른다 */}
          {tab === 'plan' && <RequestPlanView info={draft} />}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
