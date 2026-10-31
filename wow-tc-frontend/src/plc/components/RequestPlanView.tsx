import { useCallback, useEffect, useState, type ReactElement } from 'react';
import {
  Alert, Box, Button, Chip, CircularProgress, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { interfaceApi } from '../services/plcApi';
import type { InterfaceInfo, RequestPlan } from '../types/plc';

/** 프레임 16진을 2바이트씩 띄어 읽기 쉽게 */
const spaced = (hex: string) => hex.replace(/(.{4})/g, '$1 ').trim();

/**
 * 요청 미리보기 (docs/interface-detail-design.md §7.3) — 이 정의로 PLC 에 보낼 요청(기능·대상·개수·담긴 점·프레임). 보내지 않는다.
 * {@code auto} 면 열릴 때 바로, 아니면 버튼으로 부른다(폼은 편집 중이라 버튼으로).
 */
export function RequestPlanView({ info, auto }: { info: InterfaceInfo | null; auto?: boolean }): ReactElement {
  const { t } = useI18n();
  const [plan, setPlan] = useState<RequestPlan | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (!info) return;
    setLoading(true);
    try {
      setPlan(await interfaceApi.plan(info));
      setError(null);
    } catch (e) {
      setPlan(null);
      setError(extractApiError(e));
    } finally {
      setLoading(false);
    }
  }, [info]);
  useEffect(() => { if (auto) void load(); }, [auto, load]);

  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Typography variant="subtitle2">{t('i18n.tc.plan.title', '요청 미리보기')}</Typography>
        <Typography variant="caption" color="text.secondary">{t('i18n.tc.plan.help', '이 정의로 PLC 에 보낼 요청 — 보내지 않습니다')}</Typography>
        <Box sx={{ flex: 1 }} />
        <Button size="small" startIcon={loading ? <CircularProgress size={14} /> : <RefreshIcon fontSize="small" />} disabled={!info || loading}
          onClick={() => { void load(); }}>
          {plan ? t('i18n.tc.plan.again', '다시 보기') : t('i18n.tc.plan.run', '미리보기')}
        </Button>
      </Stack>
      {error && <Alert severity="error">{error}</Alert>}
      {plan && (
        <>
          <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
            <Chip size="small" color="primary" label={`${plan.plcName} · ${plan.protocol}`} />
            {Object.entries(plan.settings).map(([k, v]) => <Chip key={k} size="small" variant="outlined" label={`${k}=${String(v)}`} />)}
          </Stack>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small" aria-label="request-plan" sx={{ '& td, & th': { px: 0.75, py: 0.5 }, minWidth: 760 }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 40 }}>#</TableCell>
                  <TableCell sx={{ width: 200 }}>{t('i18n.tc.plan.function', '명령·함수')}</TableCell>
                  <TableCell sx={{ width: 90 }}>{t('i18n.tc.plan.target', '시작')}</TableCell>
                  <TableCell sx={{ width: 90 }}>{t('i18n.tc.plan.count', '개수')}</TableCell>
                  <TableCell sx={{ width: 160 }}>{t('i18n.tc.plan.points', '담긴 점')}</TableCell>
                  <TableCell>{t('i18n.tc.plan.frame', '프레임(16진)')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {plan.requests.map((r) => (
                  <TableRow key={r.seq}>
                    <TableCell>{r.seq}</TableCell>
                    <TableCell>{r.function}</TableCell>
                    <TableCell>{r.target}</TableCell>
                    <TableCell>{r.count} {r.unit}</TableCell>
                    <TableCell>{r.points.join(', ')}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.75rem', wordBreak: 'break-all' }}>{spaced(r.frameHex)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
          {plan.notes.map((n) => <Alert key={n} severity="info" sx={{ py: 0 }}>{n}</Alert>)}
        </>
      )}
    </Stack>
  );
}
