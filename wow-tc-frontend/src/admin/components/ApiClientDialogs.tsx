import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactElement } from 'react';
import {
  Alert, Autocomplete, Box, Button, Chip, Dialog, DialogContent, IconButton, InputAdornment, Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import { DataGridPro, GridActionsCellItem, type GridColDef } from '@mui/x-data-grid-pro';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import KeyIcon from '@mui/icons-material/Key';
import BlockIcon from '@mui/icons-material/Block';
import {
  DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showConfirm, showToast, useI18n,
} from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { apiClientApi } from '../services/adminApi';
import type { ApiClient, ApiKey, IssuedApiKey } from '../types/admin';

const EMPTY: ApiClient = {
  clientId: '', name: '', description: '', enabled: true, allowedOpcodes: [], allowedIps: [], allowedPushUrlPrefixes: [],
  rateLimitPerSec: null, maxConcurrent: null,
};

/** 칩 여러 개 — 목록에서 고르거나 직접 적고 Enter */
function ChipsField({ label, value, onChange, options = [], helperText, error }: {
  label: string;
  value: string[];
  onChange: (v: string[]) => void;
  options?: string[];
  helperText?: string;
  error?: boolean;
}): ReactElement {
  return (
    <Autocomplete multiple freeSolo options={options} value={value} onChange={(_e, v) => onChange(v.map((x) => x.trim()).filter(Boolean))}
      renderValue={(vals, getItemProps) => vals.map((x, i) => <Chip size="small" label={x} {...getItemProps({ index: i })} key={x} />)}
      renderInput={(p) => <TextField {...p} label={label} helperText={helperText} error={error} />} />
  );
}

const numOrNull = (s: string): number | null => (s.trim() === '' ? null : Number(s));

/**
 * API 클라이언트 등록·수정 — 허용 opcode(비우면 아무것도 못 부른다), 허용 IP(비우면 어디서나 — CIDR 가능), 결과를 보낼 URL 접두어,
 * 초당 요청·동시 요청 한도(비우면 설정값).
 */
export function ApiClientFormDialog({ open, initial, opcodes, onClose, onSubmit, error }: {
  open: boolean;
  initial?: ApiClient;
  opcodes: string[];
  onClose: () => void;
  onSubmit: (c: ApiClient) => void;
  error?: string | null;
}): ReactElement {
  const { t } = useI18n();
  const edit = !!initial;
  const [v, setV] = useState<ApiClient>(EMPTY);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => { if (open) { setV(initial ? { ...EMPTY, ...initial } : EMPTY); setErrors({}); } }, [open, initial]);
  const set = (patch: Partial<ApiClient>) => setV((s) => ({ ...s, ...patch }));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const errs: Record<string, string> = {};
    if (!/^[a-z0-9]+(-[a-z0-9]+)*$/.test(v.clientId) || v.clientId.length > 100) {
      errs.clientId = t('i18n.tc.apiclient.idRule', '영문 소문자·숫자와 하이픈(-), 100자까지');
    }
    if (v.allowedPushUrlPrefixes.some((u) => !/^https?:\/\/\S+$/.test(u))) errs.urls = t('i18n.tc.webhook.urlRule', 'http(s):// 로 시작하는 주소');
    if (v.rateLimitPerSec != null && !(v.rateLimitPerSec >= 1)) errs.rate = t('i18n.tc.apiclient.positive', '1 이상');
    if (v.maxConcurrent != null && !(v.maxConcurrent >= 1)) errs.conc = t('i18n.tc.apiclient.positive', '1 이상');
    if (Object.keys(errs).length) { setErrors(errs); return; }
    onSubmit(v);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}
      slotProps={{ paper: { component: 'form', onSubmit: submit } as never }}>
      <DialogTitleClose onClose={onClose}>{edit ? t('i18n.tc.apiclient.edit', 'API 클라이언트 수정') : t('i18n.tc.apiclient.add', 'API 클라이언트 등록')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.apiclient.clientId', '클라이언트 ID')} required value={v.clientId} onChange={(e) => set({ clientId: e.target.value })}
              error={!!errors.clientId} helperText={errors.clientId ?? t('i18n.tc.apiclient.idHelp', '예: mes-line1 — 지운 ID 는 다시 못 쓴다')}
              slotProps={{ input: { readOnly: edit } }} />
            <TextField label={t('i18n.tc.user.name', '이름')} value={v.name ?? ''} onChange={(e) => set({ name: e.target.value })} />
          </Box>
          <ChipsField label={t('i18n.tc.apiclient.opcodes', '허용 opcode')} value={v.allowedOpcodes} options={opcodes}
            onChange={(x) => set({ allowedOpcodes: x })} helperText={t('i18n.tc.apiclient.opcodesHelp', '이 클라이언트가 부를 수 있는 인터페이스 — 비우면 아무것도 못 부른다')} />
          <ChipsField label={t('i18n.tc.apiclient.ips', '허용 IP')} value={v.allowedIps}
            onChange={(x) => set({ allowedIps: x })} helperText={t('i18n.tc.apiclient.ipsHelp', 'IP 또는 CIDR(10.0.0.0/24) — 비우면 어디서나')} />
          <ChipsField label={t('i18n.tc.apiclient.urls', '결과 보낼 URL 접두어')} value={v.allowedPushUrlPrefixes} error={!!errors.urls}
            onChange={(x) => set({ allowedPushUrlPrefixes: x })} helperText={errors.urls ?? t('i18n.tc.apiclient.urlsHelp', '콜백 URL 이 이 접두어로 시작해야 보낸다')} />
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 2 }}>
            <TextField label={t('i18n.tc.apiclient.rate', '초당 요청 한도')} type="number" value={v.rateLimitPerSec ?? ''}
              onChange={(e) => set({ rateLimitPerSec: numOrNull(e.target.value) })} error={!!errors.rate}
              helperText={errors.rate ?? t('i18n.tc.apiclient.defaultHelp', '비우면 설정값')} />
            <TextField label={t('i18n.tc.apiclient.concurrent', '동시 요청 한도')} type="number" value={v.maxConcurrent ?? ''}
              onChange={(e) => set({ maxConcurrent: numOrNull(e.target.value) })} error={!!errors.conc}
              helperText={errors.conc ?? t('i18n.tc.apiclient.defaultHelp', '비우면 설정값')} />
          </Box>
          <TextField label={t('i18n.tc.common.description', '설명')} value={v.description ?? ''} onChange={(e) => set({ description: e.target.value })} />
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}><Button type="submit" variant="contained">{t('i18n.common.save', '저장')}</Button></DialogFooter>
    </Dialog>
  );
}

/**
 * 키 — 목록(상태·만료·마지막 사용), 발급(키는 이번 한 번만 보인다), 폐기. 활성 키는 둘까지라 회전은 새 키 발급 → 호출자 교체 → 옛 키 폐기.
 */
export function ApiKeysDialog({ clientId, canManage, onClose, onChanged }: {
  clientId: string | null;
  canManage: boolean;
  onClose: () => void;
  onChanged: () => void;
}): ReactElement {
  const { t } = useI18n();
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(false);
  const [days, setDays] = useState('365');
  const [issued, setIssued] = useState<IssuedApiKey | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (id: string) => {
    setLoading(true);
    try {
      setKeys((await apiClientApi.find(id)).keys ?? []);
    } catch (e) {
      setError(extractApiError(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    setKeys([]); setIssued(null); setError(null); setDays('365');
    if (clientId) void load(clientId);
  }, [clientId, load]);

  const issue = async () => {
    if (!clientId) return;
    setError(null);
    try {
      setIssued(await apiClientApi.issueKey(clientId, Number(days) || undefined));
      await load(clientId);
      onChanged();
    } catch (e) {
      setError(extractApiError(e));
    }
  };

  const revoke = useCallback((k: ApiKey) => {
    void (async () => {
      if (!clientId) return;
      const ok = await showConfirm({
        message: t('i18n.tc.apiclient.confirmRevoke', '키 {key} 를 폐기할까요? 이 키로 부르는 호출은 바로 거절됩니다.').replace('{key}', k.keyId),
        severity: 'error',
      });
      if (!ok) return;
      try {
        await apiClientApi.revokeKey(clientId, k.keyId);
        await load(clientId);
        onChanged();
      } catch (e) {
        showToast(extractApiError(e), 'error');
      }
    })();
  }, [clientId, load, onChanged, t]);

  const copy = () => {
    if (!issued) return;
    void navigator.clipboard?.writeText(issued.apiKey).then(() => showToast(t('i18n.tc.apiclient.copied', '복사했습니다'), 'success'));
  };

  const cols = useMemo<GridColDef<ApiKey>[]>(() => [
    { field: 'keyId', headerName: t('i18n.tc.apiclient.keyId', '키 ID'), width: 150 },
    {
      field: 'status', headerName: t('i18n.tc.schedule.state', '상태'), width: 120,
      renderCell: ({ row }) => {
        const label = row.status === 'REVOKED' ? t('i18n.tc.apiclient.revoked', '폐기') : row.expired ? t('i18n.tc.apiclient.expired', '만료') : t('i18n.tc.apiclient.active', '활성');
        return <Chip size="small" color={row.status === 'ACTIVE' && !row.expired ? 'success' : 'default'} label={label} />;
      },
    },
    { field: 'issuedAt', headerName: t('i18n.tc.apiclient.issuedAt', '발급'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'expiresAt', headerName: t('i18n.tc.apiclient.expiresAt', '만료'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'lastUsedAt', headerName: t('i18n.tc.apiclient.lastUsed', '마지막 사용'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    { field: 'lastUsedIp', headerName: 'IP', width: 130 },
    { field: 'revokedAt', headerName: t('i18n.tc.apiclient.revokedAt', '폐기한 때'), width: 160, valueFormatter: (v: string) => fmtTime(v) },
    {
      field: 'actions', type: 'actions', headerName: '', width: 60,
      getActions: ({ row }) => (canManage && row.status === 'ACTIVE'
        ? [<GridActionsCellItem key="revoke" icon={<BlockIcon />} label={t('i18n.tc.apiclient.revoke', '폐기')} onClick={() => revoke(row)} />]
        : []),
    },
  ], [t, canManage, revoke]);

  return (
    <Dialog open={!!clientId} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.apiclient.keys', 'API 키')} — {clientId}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          {issued && (
            <Alert severity="warning">
              <Typography variant="body2" sx={{ mb: 1 }}>
                {t('i18n.tc.apiclient.onceOnly', '이 키는 지금 한 번만 보입니다 — 복사해서 호출하는 시스템에 넣으세요. 닫으면 다시 볼 수 없습니다.')}
              </Typography>
              <TextField fullWidth size="small" value={issued.apiKey} label={`${issued.keyId} · ${t('i18n.tc.apiclient.expiresAt', '만료')} ${fmtTime(issued.expiresAt)}`}
                slotProps={{
                  input: {
                    readOnly: true, sx: { fontFamily: 'monospace' },
                    endAdornment: (
                      <InputAdornment position="end">
                        <Tooltip title={t('i18n.tc.apiclient.copy', '복사')}><IconButton size="small" onClick={copy}><ContentCopyIcon fontSize="small" /></IconButton></Tooltip>
                      </InputAdornment>
                    ),
                  },
                  htmlInput: { 'aria-label': t('i18n.tc.apiclient.newKey', '새 키') },
                }} />
            </Alert>
          )}
          {canManage && (
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <TextField size="small" type="number" label={t('i18n.tc.apiclient.days', '수명(일)')} value={days} onChange={(e) => setDays(e.target.value)} sx={{ width: 140 }} />
              <Button variant="contained" startIcon={<KeyIcon />} onClick={() => { void issue(); }}>{t('i18n.tc.apiclient.issue', '새 키 발급')}</Button>
              <Typography variant="caption" color="text.secondary">
                {t('i18n.tc.apiclient.rotateHelp', '활성 키는 둘까지 — 바꿀 때는 새 키 발급 → 호출하는 쪽 교체 → 옛 키 폐기')}
              </Typography>
            </Stack>
          )}
          <Box sx={{ height: 320, position: 'relative' }}>
            <Box sx={{ position: 'absolute', inset: 0 }}>
              <DataGridPro rows={keys} columns={cols} getRowId={(r) => r.keyId} loading={loading} density="compact" disableRowSelectionOnClick hideFooter />
            </Box>
          </Box>
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
