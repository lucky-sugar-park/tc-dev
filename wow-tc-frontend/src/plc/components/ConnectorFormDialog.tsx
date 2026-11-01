import { useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Box, Button, Dialog, DialogContent, Divider, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showConfirm, showToast, useI18n } from 'wow-mes-frontend-shared';
import { connectorApi } from '../services/plcApi';
import { SettingsFields, settingText, settingsFromText } from './SettingsFields';
import type { PlcConnectorInfo, ProtocolDescriptor } from '../types/plc';

type Form = {
  name: string;
  protocol: string;
  hostIp: string;
  ports: string;
  connectionVips: string;
  connLimitPerPort: string;
  manufacturer: string;
  description: string;
  settings: Record<string, string>;
  /** 새 비밀번호(OPC UA 로그인) — 비우면 저장된 값 그대로 */
  password: string;
};

const list = (s: string): string[] => s.split(/[,\s]+/).map((x) => x.trim()).filter(Boolean);

/**
 * 커넥터 등록·수정 (docs/frontend-design.md §5). 프로토콜을 고르면 설정 칸이 서버의 프로토콜 설명({@code /protocols})으로 바뀐다 —
 * 어댑터 클래스·헤더 템플릿·시작포트+포트수(옛 화면)는 없앴다. 빈 설정 칸은 서버 기본값.
 */
export function ConnectorFormDialog({ open, initial, protocols, onClose, onSubmit, error }: {
  open: boolean;
  initial?: PlcConnectorInfo;
  protocols: ProtocolDescriptor[];
  onClose: () => void;
  onSubmit: (info: PlcConnectorInfo) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [f, setF] = useState<Form | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open) return;
    const p = initial?.protocol ?? protocols[0]?.protocol ?? 'MELSEC_MC';
    const desc = protocols.find((d) => d.protocol === p);
    setF({
      name: initial?.name ?? '',
      protocol: p,
      hostIp: initial?.hostIp ?? '',
      ports: (initial?.ports ?? []).join(', '),
      connectionVips: (initial?.connectionVips ?? []).join(', '),
      connLimitPerPort: initial?.connLimitPerPort && initial.connLimitPerPort > 0 ? String(initial.connLimitPerPort) : '1',
      manufacturer: initial?.manufacturer ?? '',
      description: initial?.description ?? '',
      settings: Object.fromEntries((desc?.settings ?? []).map((s) => [s.key, settingText(initial?.settings?.[s.key], s)])),
      password: '',
    });
    setErrors({});
  }, [open, initial, protocols]);

  // OPC UA — 이 커넥터가 믿은 서버 인증서 지문(docs/opcua-design.md O16)
  const [thumbprint, setThumbprint] = useState<string | null>(null);
  useEffect(() => {
    setThumbprint(null);
    if (!open || !initial?.name || initial.protocol !== 'OPC_UA') return;
    connectorApi.opcUaServerCertificate(initial.name).then(setThumbprint).catch(() => setThumbprint(null));
  }, [open, initial]);

  const desc = useMemo(() => protocols.find((d) => d.protocol === f?.protocol), [protocols, f?.protocol]);
  if (!f) return <Dialog open={false} />;
  const set = (patch: Partial<Form>) => setF({ ...f, ...patch });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(f.name)) errs.name = t('i18n.tc.connector.nameRule', '영숫자·_ . - 100자까지');
    if (!f.hostIp.trim()) errs.hostIp = t('i18n.tc.common.required', '필수입니다');
    const ports = list(f.ports).map(Number);
    if (!ports.length || ports.some((n) => !Number.isInteger(n) || n < 1 || n > 65535)) errs.ports = t('i18n.tc.connector.portsRule', '1~65535 포트를 쉼표로');
    const vips = list(f.connectionVips);
    if (!vips.length) errs.connectionVips = t('i18n.tc.connector.vipsRule', '로컬 주소를 하나 이상(포트 수와 같게)');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    const settings = settingsFromText(desc?.settings ?? [], f.settings);
    onSubmit({
      ...(initial ?? {}),
      connection: undefined,
      name: f.name,
      protocol: f.protocol,
      hostIp: f.hostIp.trim(),
      ports,
      connectionVips: vips,
      connLimitPerPort: Number(f.connLimitPerPort) || 1,
      manufacturer: f.manufacturer || undefined,
      description: f.description || undefined,
      settings,
      password: f.password || undefined,
    });
  };

  const downloadClientCertificate = async () => {
    try {
      const blob = await connectorApi.opcUaClientCertificate();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'wow-tc-opcua-client.der';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  const forgetServerCertificate = async () => {
    if (!initial?.name) return;
    const ok = await showConfirm({
      message: t('i18n.tc.opcua.confirmForget', '믿은 서버 인증서를 잊을까요? 다음 연결 때 그때의 서버 인증서를 다시 처음 믿습니다(서버 인증서를 바꾼 것이 맞을 때만).'),
    });
    if (!ok) return;
    try {
      await connectorApi.opcUaForgetServerCertificate(initial.name);
      setThumbprint(null);
      showToast(t('i18n.tc.opcua.forgotten', '서버 인증서를 잊었습니다'), 'success');
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };
  const opcUa = f.protocol === 'OPC_UA';

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>
        {edit ? t('i18n.tc.connector.edit', '커넥터 수정') : t('i18n.tc.connector.add', '커넥터 등록')}
      </DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {edit && initial?.published && (
            <Alert severity="info">{t('i18n.tc.connector.editPublished', '게시 중인 커넥터는 저장한 뒤 해제·게시해야 새 설정으로 연결합니다.')}</Alert>
          )}
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField label={t('i18n.tc.connector.name', '커넥터')} required value={f.name} onChange={(e) => set({ name: e.target.value })}
              error={!!errors.name} helperText={errors.name} slotProps={{ input: { readOnly: edit } }} />
            <TextField select label={t('i18n.tc.connector.protocol', '프로토콜')} value={f.protocol}
              onChange={(e) => {
                const d = protocols.find((x) => x.protocol === e.target.value);
                set({ protocol: e.target.value, settings: Object.fromEntries((d?.settings ?? []).map((s) => [s.key, ''])) });
              }}>
              {protocols.map((p) => <MenuItem key={p.protocol} value={p.protocol}>{p.label}</MenuItem>)}
            </TextField>
            <TextField label={t('i18n.tc.connector.host', 'PLC 주소')} required value={f.hostIp} onChange={(e) => set({ hostIp: e.target.value })}
              error={!!errors.hostIp} helperText={errors.hostIp} placeholder="192.168.0.10" />
            <TextField label={t('i18n.tc.connector.ports', '포트')} required value={f.ports} onChange={(e) => set({ ports: e.target.value })}
              error={!!errors.ports} helperText={errors.ports ?? t('i18n.tc.connector.portsHelp', '쉼표로 여러 개 — 포트마다 연결 하나')} placeholder="5000, 5001" />
            <TextField label={t('i18n.tc.connector.vips', '로컬 주소(VIP)')} required value={f.connectionVips} onChange={(e) => set({ connectionVips: e.target.value })}
              error={!!errors.connectionVips} helperText={errors.connectionVips ?? t('i18n.tc.connector.vipsHelp', '포트와 같은 개수 — min(포트, VIP) 만큼 연결')} placeholder="10.0.0.5" />
            <TextField label={t('i18n.tc.connector.connLimit', '포트당 연결 수')} type="number" value={f.connLimitPerPort}
              onChange={(e) => set({ connLimitPerPort: e.target.value })} slotProps={{ htmlInput: { min: 1 } }} />
            <TextField label={t('i18n.tc.connector.manufacturer', '제조사')} value={f.manufacturer} onChange={(e) => set({ manufacturer: e.target.value })} />
            <TextField label={t('i18n.tc.common.description', '설명')} value={f.description} onChange={(e) => set({ description: e.target.value })} />
          </Box>
          <Divider />
          <Typography variant="subtitle2">{t('i18n.tc.connector.settings', '프로토콜 설정')} — {desc?.label}</Typography>
          <SettingsFields schema={desc?.settings ?? []} values={f.settings} onChange={(settings) => set({ settings })}
            fallbackLabel={(s) => `${t('i18n.tc.connector.default', '기본')} ${settingText(s.defaultValue, s)}`} />
          {opcUa && (
            <>
              <Divider />
              <Typography variant="subtitle2">{t('i18n.tc.opcua.security', 'OPC UA 로그인·인증서')}</Typography>
              {f.settings.serverTrust === 'TRUST_ALL' && (
                <Alert severity="warning">{t('i18n.tc.opcua.trustAllWarning', 'TRUST_ALL 은 서버 인증서를 검사하지 않습니다 — 시험용으로만 쓰세요.')}</Alert>
              )}
              <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
                <TextField label={t('i18n.tc.opcua.password', '로그인 비밀번호')} type="password" value={f.password}
                  onChange={(e) => set({ password: e.target.value })} autoComplete="new-password"
                  helperText={initial?.passwordSet
                    ? t('i18n.tc.opcua.passwordSet', '저장됨 — 비우면 그대로 둡니다')
                    : t('i18n.tc.opcua.passwordHelp', 'authMode=USERNAME 일 때 — TC_SECRET_KEY 로 암호화해 저장')}
                  slotProps={{ htmlInput: { 'aria-label': 'opcua-password' } }} />
                <Box>
                  <Typography variant="caption" color="text.secondary" component="div">
                    {t('i18n.tc.opcua.serverCert', '믿은 서버 인증서')}: {thumbprint ?? t('i18n.tc.opcua.noServerCert', '없음(처음 연결 때 믿음)')}
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                    <Button size="small" variant="outlined" onClick={() => { void downloadClientCertificate(); }}>
                      {t('i18n.tc.opcua.clientCert', 'tc 클라이언트 인증서 내려받기')}
                    </Button>
                    {edit && thumbprint && (
                      <Button size="small" color="warning" variant="outlined" onClick={() => { void forgetServerCertificate(); }}>
                        {t('i18n.tc.opcua.forget', '서버 인증서 잊기')}
                      </Button>
                    )}
                  </Stack>
                </Box>
              </Box>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}
