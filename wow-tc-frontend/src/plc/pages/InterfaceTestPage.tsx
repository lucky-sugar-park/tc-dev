import { useEffect, useMemo, useState, type ReactElement } from 'react';
import {
  Alert, Autocomplete, Box, Button, Chip, FormControlLabel, Paper, Stack, Switch, Table, TableBody, TableCell, TableRow, TextField, Typography,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import { PageContainer, extractApiError, showToast, useI18n } from 'wow-mes-frontend-shared';
import { interfaceApi, sendPlcRequest, type TcResponse } from '../services/plcApi';
import { usePerm } from '../../shared/usePerm';
import type { InterfaceInfo, PointInfo } from '../types/plc';

const SCREEN = 'tc.plc.interface-test';

/** 점 형에 맞는 예시 값 — 쓰기 요청 양식 */
function sample(p: PointInfo): unknown {
  const one = (): unknown => {
    switch (p.type) {
      case 'BOOL': return false;
      case 'FLOAT32': case 'FLOAT64': return 0.0;
      case 'STRING': return '';
      case 'BYTES': return '';
      default: return 0;
    }
  };
  return (p.count ?? 1) > 1 ? Array.from({ length: p.count ?? 1 }, one) : one();
}

/** 인터페이스 정의로 요청 data 양식을 만든다 — 읽기는 빈 값, 쓰기는 점 이름마다 예시 값 */
function template(i: InterfaceInfo): Record<string, unknown> {
  if (i.interfaceType !== 'WRITE') return {};
  return Object.fromEntries((i.points ?? []).map((p) => [p.name, sample(p)]));
}

type Sent = { at: number; opcode: string; status: number; body: TcResponse; elapsedMs: number; transactionId: string };

/**
 * 인터페이스 시험 (docs/frontend-design.md §5) — 인터페이스를 고르면 정의로 요청 data 양식을 채워 준다(옛 화면은 JSON 직접 입력·검증 없음).
 * 동기(request-and-reply)·접수만(request)으로 보내고, 응답·PLC 상태·걸린 시간을 본다. 실제 PLC 로 간다 — 쓰기는 PLC 값을 바꾼다.
 */
export default function InterfaceTestPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const [list, setList] = useState<InterfaceInfo[]>([]);
  const [selected, setSelected] = useState<InterfaceInfo | null>(null);
  const [json, setJson] = useState('{}');
  const [reply, setReply] = useState(true);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState<Sent[]>([]);

  useEffect(() => {
    interfaceApi.list().then((rows) => setList(rows.filter((r) => r.interfaceType === 'READ' || r.interfaceType === 'WRITE')))
      .catch((e) => showToast(extractApiError(e), 'error'));
  }, []);

  const pick = async (i: InterfaceInfo | null) => {
    setSelected(i);
    if (!i) return;
    try {
      const full = await interfaceApi.find(i.name);
      setSelected(full);
      setJson(JSON.stringify(template(full), null, 2));
      setReply(full.reply !== false);
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  const parsed = useMemo(() => {
    try {
      const v = JSON.parse(json);
      return v && typeof v === 'object' && !Array.isArray(v) ? { ok: true as const, value: v as Record<string, unknown> } : { ok: false as const };
    } catch {
      return { ok: false as const };
    }
  }, [json]);

  const send = async () => {
    if (!selected || !parsed.ok) return;
    setBusy(true);
    try {
      const r = await sendPlcRequest(selected.name, parsed.value, reply);
      setSent((s) => [{ at: Date.now(), opcode: selected.name, ...r }, ...s].slice(0, 20));
    } catch (e) {
      showToast(extractApiError(e), 'error');
    } finally {
      setBusy(false);
    }
  };

  const last = sent[0];

  return (
    <PageContainer>
      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 1, flex: 1, minHeight: 0 }}>
        <Paper variant="outlined" sx={{ p: 1.5, flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
          <Autocomplete options={list} value={selected} getOptionLabel={(o) => `${o.name} (${o.interfaceType})`}
            isOptionEqualToValue={(a, b) => a.name === b.name} onChange={(_e, v) => { void pick(v); }}
            renderInput={(p) => <TextField {...p} size="small" label={t('i18n.tc.interfaceTest.pick', '인터페이스(opcode)')} />} />
          {selected && (
            <Typography variant="caption" color="text.secondary">
              PLC {selected.plcName} · {selected.interfaceType} · {(selected.points ?? []).map((p) => `${p.name}=${p.address}`).join(', ')}
            </Typography>
          )}
          {selected?.interfaceType === 'WRITE' && (
            <Alert severity="warning" sx={{ py: 0 }}>{t('i18n.tc.interfaceTest.writeWarn', '쓰기 요청은 실제 PLC 값을 바꿉니다.')}</Alert>
          )}
          <TextField label={t('i18n.tc.interfaceTest.data', '요청 data (JSON)')} multiline minRows={8} value={json}
            onChange={(e) => setJson(e.target.value)} error={!parsed.ok}
            helperText={parsed.ok ? ' ' : t('i18n.tc.interfaceTest.badJson', 'JSON 객체가 아닙니다')}
            slotProps={{ htmlInput: { style: { fontFamily: 'monospace', fontSize: 13 } } }} />
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <FormControlLabel control={<Switch checked={reply} onChange={(e) => setReply(e.target.checked)} />}
              label={reply ? t('i18n.tc.interfaceTest.sync', '응답 받기(request-and-reply)') : t('i18n.tc.interfaceTest.async', '접수만(request)')} />
            <Box sx={{ flex: 1 }} />
            <Button variant="contained" startIcon={<SendIcon />} disabled={!perm('X') || !selected || !parsed.ok || busy} onClick={() => { void send(); }}>
              {t('i18n.tc.interfaceTest.send', '보내기')}
            </Button>
          </Stack>
        </Paper>
        <Paper variant="outlined" sx={{ p: 1.5, flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 1, overflow: 'auto' }}>
          <Typography variant="subtitle2">{t('i18n.tc.interfaceTest.result', '응답')}</Typography>
          {!last && <Typography variant="body2" color="text.secondary">{t('i18n.tc.interfaceTest.none', '아직 보낸 요청이 없습니다.')}</Typography>}
          {last && (
            <>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip size="small" label={`HTTP ${last.status}`} color={last.status === 200 ? 'success' : 'error'} />
                {last.body?.success !== undefined && (
                  <Chip size="small" label={last.body.success ? 'success' : (last.body.errorCode ?? 'fail')} color={last.body.success ? 'success' : 'error'} />
                )}
                {last.body?.plcStatus && (
                  <Chip size="small" variant="outlined" label={`${last.body.plcStatus.protocol} ${last.body.plcStatus.code === 0 ? 'OK' : last.body.plcStatus.text}`}
                    color={last.body.plcStatus.code === 0 ? 'success' : 'error'} />
                )}
                <Typography variant="caption" color="text.secondary">{last.elapsedMs} ms · {last.transactionId}</Typography>
              </Stack>
              {last.body?.errorMessage && <Alert severity="error">{last.body.errorMessage}</Alert>}
              {last.body?.data && Object.keys(last.body.data).length > 0 && (
                <Table size="small">
                  <TableBody>
                    {Object.entries(last.body.data).map(([k, v]) => (
                      <TableRow key={k}><TableCell sx={{ width: '35%' }}>{k}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</TableCell></TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
              <TextField label="JSON" multiline value={JSON.stringify(last.body, null, 2)} minRows={6}
                slotProps={{ input: { readOnly: true }, htmlInput: { style: { fontFamily: 'monospace', fontSize: 12 } } }} />
            </>
          )}
          {sent.length > 1 && (
            <>
              <Typography variant="subtitle2" sx={{ mt: 1 }}>{t('i18n.tc.interfaceTest.history', '이번에 보낸 요청')}</Typography>
              {sent.slice(1).map((s) => (
                <Typography key={s.transactionId} variant="caption" sx={{ fontFamily: 'monospace' }}>
                  {new Date(s.at).toLocaleTimeString()} {s.opcode} HTTP {s.status} {s.body?.success === false ? s.body.errorCode : ''} {s.elapsedMs}ms
                </Typography>
              ))}
            </>
          )}
        </Paper>
      </Box>
    </PageContainer>
  );
}
