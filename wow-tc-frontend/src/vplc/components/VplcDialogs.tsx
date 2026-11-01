import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Autocomplete, Box, Button, Chip, Dialog, DialogContent, FormControlLabel, MenuItem, Paper, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { MEMORY_TYPES, PROTOCOL_LABEL, protocolOf, vplcApi } from '../services/vplcApi';
import type { Vplc, VplcConnection, VplcProtocol } from '../types/vplc';

const EMPTY: Vplc = {
  id: '', name: '', protocol: 'MELSEC_MC', frameFormat: 'BINARY', manufacturer: 'MITSUBISHI', ipAddress: '127.0.0.1', startPort: 15100, portCount: 1,
  portConnLimit: 4, description: '', memoryTypeList: ['D*', 'M*'], published: false,
};

/** 종류를 고르면 바뀌는 기본값 — Modbus·OPC UA 는 포트 하나(메모리 종류는 vplc 가 채운다) */
const DEFAULTS: Record<VplcProtocol, Partial<Vplc>> = {
  MELSEC_MC: { startPort: 15100, portConnLimit: 4, manufacturer: 'MITSUBISHI', memoryTypeList: ['D*', 'M*'] },
  MODBUS_TCP: { startPort: 15021, portConnLimit: 8, manufacturer: '', memoryTypeList: [] },
  OPC_UA: { startPort: 4841, portConnLimit: 8, manufacturer: '', memoryTypeList: [] },
};

/** 종류별 설정 칸(글) — 저장할 때 settings 로 */
type SettingsForm = { unitId: string; size: string; delayMs: string; path: string; requireLogin: boolean; trustAllClients: boolean };

const settingsForm = (v?: Vplc): SettingsForm => {
  const s = v?.settings ?? {};
  return {
    unitId: String(s.unitId ?? 1), size: String(s.size ?? 10000), delayMs: String(s.delayMs ?? 0),
    path: String(s.path ?? '/vplc'), requireLogin: s.requireLogin === true, trustAllClients: s.trustAllClients !== false,
  };
};

const int = (s: string) => (/^-?\d+$/.test(s.trim()) ? Number(s) : NaN);

/**
 * 가상 PLC 등록·수정 — 종류(MELSEC MC·Modbus TCP·OPC UA, 등록 때만 고른다)와 이름·IP·포트·포트당 연결, 종류별 칸.
 * MELSEC: 프레임·포트 수·메모리 종류, Modbus: 단위 ID·칸 수·응답 지연(같은 포트에 단위 ID 를 달리 두면 게이트웨이 뒤 장치들),
 * OPC UA: 엔드포인트 경로·로그인 필요·클라이언트 인증서 모두 믿기(태그는 메모리 화면에서). 포트는 실행 중에는 다시 시작해야 반영된다.
 */
export function VplcFormDialog({ open, initial, onClose, onSubmit, error }: {
  open: boolean;
  initial?: Vplc;
  onClose: () => void;
  onSubmit: (v: Vplc) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<Vplc>(EMPTY);
  const [st, setSt] = useState<SettingsForm>(settingsForm());
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!open) return;
    setV(initial ? { ...EMPTY, ...initial, protocol: protocolOf(initial) } : EMPTY);
    setSt(settingsForm(initial));
    setErrors({});
  }, [open, initial]);
  const set = (patch: Partial<Vplc>) => setV((s) => ({ ...s, ...patch }));
  const setS = (patch: Partial<SettingsForm>) => setSt((s) => ({ ...s, ...patch }));
  const running = initial?.status === 'RUNNING' || initial?.status === 'PAUSED';
  const protocol = protocolOf(v);
  const mc = protocol === 'MELSEC_MC';

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(v.name)) errs.name = t('i18n.tc.connector.nameRule', '영숫자·_ . - 100자까지');
    if (!(v.startPort >= 1 && v.startPort + (mc ? v.portCount : 1) - 1 <= 65535)) errs.port = t('i18n.tc.vplc.portRule', '1~65535 안');
    if (mc && !(v.portCount >= 1 && v.portCount <= 32)) errs.portCount = '1~32';
    if (!(v.portConnLimit >= 1)) errs.conn = t('i18n.tc.apiclient.positive', '1 이상');
    if (mc && v.memoryTypeList.length === 0) errs.mem = t('i18n.tc.vplc.memRequired', '메모리 종류를 하나 이상');
    let settings: Record<string, unknown> | undefined;
    if (protocol === 'MODBUS_TCP') {
      const unitId = int(st.unitId);
      const size = int(st.size);
      const delayMs = int(st.delayMs);
      if (!(unitId >= 0 && unitId <= 255)) errs.unitId = '0~255';
      if (!(size >= 1 && size <= 65536)) errs.size = '1~65536';
      if (!(delayMs >= 0)) errs.delayMs = t('i18n.tc.vplc.zeroOrMore', '0 이상');
      settings = { unitId, size, delayMs };
    } else if (protocol === 'OPC_UA') {
      if (!/^\/?[A-Za-z0-9_\-/.]{0,100}$/.test(st.path)) errs.path = t('i18n.tc.vplc.pathRule', '영숫자·_ - / .');
      settings = { path: st.path, requireLogin: st.requireLogin, trustAllClients: st.trustAllClients };
    }
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit({ ...v, protocol, ...(mc ? {} : { portCount: 1, settings }) });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.vplc.edit', '가상 PLC 수정') : t('i18n.tc.vplc.add', '가상 PLC 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {running && <Alert severity="info">{t('i18n.tc.vplc.runningHelp', '실행 중에는 포트를 바꿔도 다시 시작해야 반영됩니다.')}</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 2fr 1fr', gap: 2 }}>
            <TextField select label={t('i18n.tc.vplc.kind', '종류')} value={protocol} disabled={edit}
              onChange={(e) => { const p = e.target.value as VplcProtocol; set({ protocol: p, ...DEFAULTS[p] }); setErrors({}); }}
              helperText={edit ? t('i18n.tc.vplc.kindFixed', '종류는 바꿀 수 없습니다') : undefined}>
              {(Object.keys(PROTOCOL_LABEL) as VplcProtocol[]).map((p) => <MenuItem key={p} value={p}>{PROTOCOL_LABEL[p]}</MenuItem>)}
            </TextField>
            <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
              error={!!errors.name} helperText={errors.name} />
            {mc ? (
              <TextField select label={t('i18n.tc.vplc.frame', '프레임')} value={v.frameFormat} onChange={(e) => set({ frameFormat: e.target.value as Vplc['frameFormat'] })}>
                <MenuItem value="BINARY">BINARY</MenuItem>
                <MenuItem value="ASCII">ASCII</MenuItem>
              </TextField>
            ) : <Box />}
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: mc ? '2fr 1fr 1fr 1fr' : '2fr 1fr 1fr', gap: 2 }}>
            <TextField label="IP" value={v.ipAddress ?? ''} onChange={(e) => set({ ipAddress: e.target.value })} />
            <TextField label={mc ? t('i18n.tc.vplc.startPort', '시작 포트') : t('i18n.tc.vplc.port', '포트')} type="number" value={v.startPort}
              onChange={(e) => set({ startPort: Number(e.target.value) })} error={!!errors.port} helperText={errors.port} />
            {mc && (
              <TextField label={t('i18n.tc.vplc.portCount', '포트 수')} type="number" value={v.portCount} onChange={(e) => set({ portCount: Number(e.target.value) })}
                error={!!errors.portCount} helperText={errors.portCount} />
            )}
            <TextField label={t('i18n.tc.vplc.connLimit', '포트당 연결')} type="number" value={v.portConnLimit} onChange={(e) => set({ portConnLimit: Number(e.target.value) })}
              error={!!errors.conn} helperText={errors.conn} />
          </Box>
          {mc && (
            <Autocomplete multiple options={MEMORY_TYPES.map((m) => m.code)} value={v.memoryTypeList}
              getOptionLabel={(c) => MEMORY_TYPES.find((m) => m.code === c)?.label ?? c}
              onChange={(_e, val) => set({ memoryTypeList: val })}
              renderValue={(vals, getItemProps) => vals.map((x, i) => <Chip size="small" label={x.replace('*', '')} {...getItemProps({ index: i })} key={x} />)}
              renderInput={(p) => <TextField {...p} label={t('i18n.tc.vplc.memoryTypes', '메모리 종류')} error={!!errors.mem} helperText={errors.mem} />} />
          )}
          {protocol === 'MODBUS_TCP' && (
            <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 2 }}>
              <TextField label={t('i18n.tc.vplc.unitId', '단위 ID')} type="number" value={st.unitId} onChange={(e) => setS({ unitId: e.target.value })}
                error={!!errors.unitId} helperText={errors.unitId ?? t('i18n.tc.vplc.unitIdHelp', '같은 포트에 단위 ID 를 달리 두면 게이트웨이 뒤 장치들')} />
              <TextField label={t('i18n.tc.vplc.size', '칸 수')} type="number" value={st.size} onChange={(e) => setS({ size: e.target.value })}
                error={!!errors.size} helperText={errors.size ?? t('i18n.tc.vplc.sizeHelp', 'HR·IR·CO·DI 영역마다')} />
              <TextField label={t('i18n.tc.vplc.delayMs', '응답 지연(ms)')} type="number" value={st.delayMs} onChange={(e) => setS({ delayMs: e.target.value })}
                error={!!errors.delayMs} helperText={errors.delayMs} />
            </Box>
          )}
          {protocol === 'OPC_UA' && (
            <>
              <TextField label={t('i18n.tc.vplc.endpointPath', '엔드포인트 경로')} value={st.path} onChange={(e) => setS({ path: e.target.value })}
                error={!!errors.path}
                helperText={errors.path ?? `opc.tcp://${v.ipAddress || '127.0.0.1'}:${v.startPort}${st.path} — ${t('i18n.tc.vplc.tagsInMemory', '태그는 메모리 보기·제어 화면에서')}`} />
              <Stack direction="row" spacing={3}>
                <FormControlLabel control={<Switch checked={st.requireLogin} onChange={(e) => setS({ requireLogin: e.target.checked })} />}
                  label={t('i18n.tc.vplc.requireLogin', '로그인 필요(익명 거절)')} />
                <FormControlLabel control={<Switch checked={st.trustAllClients} onChange={(e) => setS({ trustAllClients: e.target.checked })} />}
                  label={t('i18n.tc.vplc.trustAllClients', '클라이언트 인증서 모두 믿기')} />
              </Stack>
            </>
          )}
          {mc && <TextField label={t('i18n.tc.vplc.manufacturer', '제조사')} value={v.manufacturer ?? ''} onChange={(e) => set({ manufacturer: e.target.value })} />}
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

/** Modbus 응답 지연 바꾸기 — 메모리에 바로, 설정에도 남는다 */
export function DelayDialog({ vplc, onClose, onSubmit }: {
  vplc: Vplc | null;
  onClose: () => void;
  onSubmit: (delayMs: number) => void;
}): ReactElement {
  const { t } = useI18n();
  const [value, setValue] = useState('0');
  const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (vplc) { setValue(String(vplc.settings?.delayMs ?? 0)); setError(null); } }, [vplc]);
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const n = int(value);
    if (!(n >= 0 && n <= 600000)) { setError('0~600000'); return; }
    onSubmit(n);
  };
  return (
    <Dialog open={!!vplc} onClose={onClose} maxWidth="xs" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.vplc.delay', '응답 지연')} — {vplc?.name}</DialogTitleClose>
      <DialogContent dividers>
        <TextField fullWidth label={t('i18n.tc.vplc.delayMs', '응답 지연(ms)')} type="number" value={value} autoFocus onChange={(e) => setValue(e.target.value)}
          error={!!error} helperText={error ?? t('i18n.tc.vplc.delayHelp', '요청마다 이만큼 늦게 답한다 — tc 시간 초과 시험')} sx={{ mt: 0.5 }} />
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

/** 왼쪽 머리 칸 — 이름과 (합계) */
function HeadCell({ title, sum }: { title: string; sum: string }): ReactElement {
  return (
    <Box component="th" scope="row" sx={{
      bgcolor: 'primary.main', color: 'primary.contrastText', fontWeight: 600, fontSize: 13, px: 1.5, py: 1, minWidth: 150,
      textAlign: 'center', border: 1, borderColor: 'divider', position: 'sticky', left: 0, zIndex: 1, whiteSpace: 'nowrap',
    }}>
      {title}<Box sx={{ fontWeight: 500 }}>( {sum} )</Box>
    </Box>
  );
}

const cellSx = { border: 1, borderColor: 'divider', px: 1.5, py: 1, minWidth: 96, textAlign: 'center', fontSize: 13, verticalAlign: 'middle' } as const;

/**
 * 연결 클라이언트 보기 — 포트마다 연결 수와 붙어 있는 클라이언트(tc 커넥터 등)를 가로 표로(VPLC_CONNECTED_CLIENT).
 * 포트가 많으면 표가 옆으로 넘어가고 왼쪽 머리 칸은 고정이다. 클라이언트 칸에 마우스를 올리면 연결된 때·채널.
 */
export function VplcConnectionsDialog({ vplc, onClose }: { vplc: Vplc | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [conns, setConns] = useState<VplcConnection[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const load = useCallback((v: Vplc) => {
    setError(null); setLoading(true);
    vplcApi.connections(v.id)
      .then((c) => setConns([...c].sort((a, b) => a.port - b.port)))
      .catch((e) => setError(extractApiError(e)))
      .finally(() => setLoading(false));
  }, []);
  useEffect(() => {
    setConns([]);
    if (vplc) load(vplc);
  }, [vplc, load]);
  const totalClients = conns.reduce((n, c) => n + (c.connectedCount ?? 0), 0);
  const unit = t('i18n.tc.vplc.ea', '개');
  return (
    <Dialog open={!!vplc} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.vplc.viewClients', '연결 클라이언트 보기')}</DialogTitleClose>
      <DialogContent dividers>
        {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
        <Paper variant="outlined" sx={{ p: 2 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1.5 }}>
            <Typography variant="subtitle1">{t('i18n.tc.vplc.vplc', '가상 PLC')} :</Typography>
            <Typography variant="subtitle1" color="primary" sx={{ fontWeight: 600 }}>{vplc?.name}</Typography>
            {vplc && <Chip size="small" variant="outlined" label={PROTOCOL_LABEL[protocolOf(vplc)]} />}
            {vplc && <Typography variant="body2" color="text.secondary">{vplc.ipAddress}</Typography>}
            <Box sx={{ flex: 1 }} />
            <Button size="small" startIcon={<RefreshIcon />} disabled={loading || !vplc} onClick={() => vplc && load(vplc)}>
              {t('i18n.tc.common.refresh', '새로 고침')}
            </Button>
          </Stack>
          {conns.length === 0 && !loading ? (
            <Typography color="text.secondary" sx={{ py: 3, textAlign: 'center' }}>
              {t('i18n.tc.vplc.noPorts', '포트가 없습니다 — 게시한 가상 PLC 만 포트가 있습니다.')}
            </Typography>
          ) : (
            <Box sx={{ overflowX: 'auto' }}>
              <Box component="table" aria-label="connected-clients" sx={{ borderCollapse: 'collapse', minWidth: '100%' }}>
                <tbody>
                  <tr>
                    <HeadCell title={t('i18n.tc.vplc.portNumber', '포트 번호')} sum={`${conns.length} ${unit}`} />
                    {conns.map((c) => <Box component="td" key={c.port} sx={{ ...cellSx, fontWeight: 600 }}>{c.port}</Box>)}
                  </tr>
                  <tr>
                    <HeadCell title={t('i18n.tc.vplc.connectionCount', '연결 수')} sum={`${totalClients} ${unit}`} />
                    {conns.map((c) => (
                      <Box component="td" key={c.port} sx={{ ...cellSx, color: c.connectedCount > 0 ? 'success.main' : 'text.secondary', fontWeight: c.connectedCount > 0 ? 700 : 400 }}>
                        {c.connectedCount ?? 0}
                      </Box>
                    ))}
                  </tr>
                  <tr>
                    <HeadCell title={t('i18n.tc.vplc.clientIp', '클라이언트 IP')} sum={`${totalClients} ${unit}`} />
                    {conns.map((c) => (
                      <Box component="td" key={c.port} sx={cellSx}>
                        {c.connectedClientList?.length ? c.connectedClientList.map((x, i) => (
                          <Tooltip key={x.clientChannelId ?? i} arrow
                            title={`${t('i18n.tc.vplc.connectedAt', '연결된 때')}: ${fmtTime(x.connTimestamp)} · ${t('i18n.tc.vplc.channel', '채널')}: ${x.clientChannelId ?? '-'}`}>
                            <Box sx={{ whiteSpace: 'nowrap' }}>{x.clientIp ?? '-'}</Box>
                          </Tooltip>
                        )) : <Box sx={{ color: 'text.disabled' }}>-</Box>}
                      </Box>
                    ))}
                  </tr>
                </tbody>
              </Box>
            </Box>
          )}
        </Paper>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
