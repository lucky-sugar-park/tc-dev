import { useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Autocomplete, Box, Button, Chip, Dialog, DialogContent, MenuItem, Stack, TextField } from '@mui/material';
import { DataGridPro, type GridColDef } from '@mui/x-data-grid-pro';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { MEMORY_TYPES, vplcApi } from '../services/vplcApi';
import type { ModbusDevice, Vplc, VplcConnection } from '../types/vplc';

const EMPTY: Vplc = {
  id: '', name: '', frameFormat: 'BINARY', manufacturer: 'MITSUBISHI', ipAddress: '127.0.0.1', startPort: 15100, portCount: 1, portConnLimit: 4,
  description: '', memoryTypeList: ['D*', 'M*'], published: false,
};

/** 가상 PLC 등록·수정 — 이름·프레임(BINARY·ASCII)·포트(시작·수·포트당 연결)·메모리 종류. 포트는 실행 중에는 바꿀 수 없다 */
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
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => { if (open) { setV(initial ? { ...EMPTY, ...initial } : EMPTY); setErrors({}); } }, [open, initial]);
  const set = (patch: Partial<Vplc>) => setV((s) => ({ ...s, ...patch }));
  const running = initial?.status === 'RUNNING' || initial?.status === 'PAUSED';

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(v.name)) errs.name = t('i18n.tc.connector.nameRule', '영숫자·_ . - 100자까지');
    if (!(v.startPort >= 1 && v.startPort + v.portCount - 1 <= 65535)) errs.port = t('i18n.tc.vplc.portRule', '1~65535 안');
    if (!(v.portCount >= 1 && v.portCount <= 32)) errs.portCount = '1~32';
    if (!(v.portConnLimit >= 1)) errs.conn = t('i18n.tc.apiclient.positive', '1 이상');
    if (v.memoryTypeList.length === 0) errs.mem = t('i18n.tc.vplc.memRequired', '메모리 종류를 하나 이상');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit(v);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.vplc.edit', '가상 PLC 수정') : t('i18n.tc.vplc.add', '가상 PLC 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {running && <Alert severity="info">{t('i18n.tc.vplc.runningHelp', '실행 중에는 포트를 바꿔도 다시 시작해야 반영됩니다.')}</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} onChange={(e) => set({ name: e.target.value })}
              error={!!errors.name} helperText={errors.name} />
            <TextField select label={t('i18n.tc.vplc.frame', '프레임')} value={v.frameFormat} onChange={(e) => set({ frameFormat: e.target.value as Vplc['frameFormat'] })}>
              <MenuItem value="BINARY">BINARY</MenuItem>
              <MenuItem value="ASCII">ASCII</MenuItem>
            </TextField>
          </Box>
          <Box sx={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr', gap: 2 }}>
            <TextField label="IP" value={v.ipAddress ?? ''} onChange={(e) => set({ ipAddress: e.target.value })} />
            <TextField label={t('i18n.tc.vplc.startPort', '시작 포트')} type="number" value={v.startPort} onChange={(e) => set({ startPort: Number(e.target.value) })}
              error={!!errors.port} helperText={errors.port} />
            <TextField label={t('i18n.tc.vplc.portCount', '포트 수')} type="number" value={v.portCount} onChange={(e) => set({ portCount: Number(e.target.value) })}
              error={!!errors.portCount} helperText={errors.portCount} />
            <TextField label={t('i18n.tc.vplc.connLimit', '포트당 연결')} type="number" value={v.portConnLimit} onChange={(e) => set({ portConnLimit: Number(e.target.value) })}
              error={!!errors.conn} helperText={errors.conn} />
          </Box>
          <Autocomplete multiple options={MEMORY_TYPES.map((m) => m.code)} value={v.memoryTypeList}
            getOptionLabel={(c) => MEMORY_TYPES.find((m) => m.code === c)?.label ?? c}
            onChange={(_e, val) => set({ memoryTypeList: val })}
            renderValue={(vals, getItemProps) => vals.map((x, i) => <Chip size="small" label={x.replace('*', '')} {...getItemProps({ index: i })} key={x} />)}
            renderInput={(p) => <TextField {...p} label={t('i18n.tc.vplc.memoryTypes', '메모리 종류')} error={!!errors.mem} helperText={errors.mem} />} />
          <TextField label={t('i18n.tc.vplc.manufacturer', '제조사')} value={v.manufacturer ?? ''} onChange={(e) => set({ manufacturer: e.target.value })} />
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

export type ModbusDeviceForm = { name: string; port: number; unitId: number; size: number; delayMs: number };

const MODBUS_EMPTY: ModbusDeviceForm = { name: '', port: 15021, unitId: 1, size: 1000, delayMs: 0 };

/**
 * 가상 Modbus 장치 더하기·지연 바꾸기(vplc-modbus) — 장치 = 포트 + 단위 ID. 같은 포트에 단위 ID 를 달리 두면 게이트웨이 뒤 장치들.
 * 이미 있는 장치는 지연(ms)만 바꾼다(포트·단위 ID·칸은 지우고 다시 더한다).
 */
export function ModbusDeviceDialog({ open, initial, onClose, onSubmit, error }: {
  open: boolean;
  initial?: ModbusDevice;
  onClose: () => void;
  onSubmit: (d: ModbusDeviceForm) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<ModbusDeviceForm>(MODBUS_EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!open) return;
    setV(initial ? { name: initial.name, port: initial.port, unitId: initial.unitId, size: initial.size, delayMs: initial.delayMs } : MODBUS_EMPTY);
    setErrors({});
  }, [open, initial]);
  const set = (patch: Partial<ModbusDeviceForm>) => setV((s) => ({ ...s, ...patch }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[A-Za-z0-9_.-]{1,64}$/.test(v.name)) errs.name = t('i18n.tc.vplc.modbusNameRule', '영숫자·_ . - 64자까지');
    if (!(Number.isInteger(v.port) && v.port >= 1 && v.port <= 65535)) errs.port = t('i18n.tc.vplc.portRule', '1~65535 안');
    if (!(Number.isInteger(v.unitId) && v.unitId >= 0 && v.unitId <= 255)) errs.unitId = '0~255';
    if (!(Number.isInteger(v.size) && v.size >= 1 && v.size <= 65536)) errs.size = '1~65536';
    if (!(Number.isInteger(v.delayMs) && v.delayMs >= 0)) errs.delayMs = t('i18n.tc.vplc.zeroOrMore', '0 이상');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit(v);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>
        {edit ? t('i18n.tc.vplc.modbusEdit', 'Modbus 장치 — 응답 지연') : t('i18n.tc.vplc.modbusAdd', 'Modbus 장치 추가')}
      </DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          {!edit && (
            <Alert severity="info">
              {t('i18n.tc.vplc.modbusAddHelp', '가상 Modbus 장비는 메모리에만 둡니다 — 장비를 다시 띄우면 설정(application.yml)의 장치만 남습니다.')}
            </Alert>
          )}
          <TextField label={t('i18n.tc.point.name', '이름')} required value={v.name} disabled={edit} onChange={(e) => set({ name: e.target.value })}
            error={!!errors.name} helperText={errors.name} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.vplc.port', '포트')} type="number" value={v.port} disabled={edit} onChange={(e) => set({ port: Number(e.target.value) })}
              error={!!errors.port} helperText={errors.port} />
            <TextField label={t('i18n.tc.vplc.unitId', '단위 ID')} type="number" value={v.unitId} disabled={edit}
              onChange={(e) => set({ unitId: Number(e.target.value) })} error={!!errors.unitId} helperText={errors.unitId} />
            <TextField label={t('i18n.tc.vplc.size', '칸 수')} type="number" value={v.size} disabled={edit} onChange={(e) => set({ size: Number(e.target.value) })}
              error={!!errors.size} helperText={errors.size} />
            <TextField label={t('i18n.tc.vplc.delayMs', '응답 지연(ms)')} type="number" value={v.delayMs} onChange={(e) => set({ delayMs: Number(e.target.value) })}
              error={!!errors.delayMs} helperText={errors.delayMs} />
          </Box>
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

type ClientRow = { key: string; port: number; clientIp?: string; clientChannelId?: string; connTimestamp?: string };

/** 포트별 연결과 붙어 있는 클라이언트(tc 커넥터 등) */
export function VplcConnectionsDialog({ vplc, onClose }: { vplc: Vplc | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [conns, setConns] = useState<VplcConnection[]>([]);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    setConns([]); setError(null);
    if (vplc) vplcApi.connections(vplc.id).then(setConns).catch((e) => setError(extractApiError(e)));
  }, [vplc]);
  const rows = useMemo<ClientRow[]>(() => conns.flatMap((c) => (c.connectedClientList?.length
    ? c.connectedClientList.map((x, i) => ({ key: `${c.port}-${i}`, port: c.port, clientIp: x.clientIp, clientChannelId: x.clientChannelId, connTimestamp: x.connTimestamp }))
    : [{ key: `${c.port}-none`, port: c.port }])), [conns]);
  const cols = useMemo<GridColDef<ClientRow>[]>(() => [
    { field: 'port', headerName: t('i18n.tc.vplc.port', '포트'), width: 90 },
    { field: 'clientIp', headerName: t('i18n.tc.vplc.client', '클라이언트'), width: 160, valueGetter: (v) => v ?? t('i18n.tc.vplc.noClient', '(없음)') },
    { field: 'connTimestamp', headerName: t('i18n.tc.vplc.connectedAt', '연결된 때'), width: 170, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'clientChannelId', headerName: t('i18n.tc.vplc.channel', '채널'), flex: 1, minWidth: 160 },
  ], [t]);
  return (
    <Dialog open={!!vplc} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.vplc.connections', '연결')} — {vplc?.name}</DialogTitleClose>
      <DialogContent dividers>
        {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
        <Box sx={{ height: 320, position: 'relative' }}>
          <Box sx={{ position: 'absolute', inset: 0 }}>
            <DataGridPro rows={rows} columns={cols} getRowId={(r) => r.key} density="compact" disableRowSelectionOnClick hideFooter />
          </Box>
        </Box>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
