import { useEffect, useState, type ReactElement } from 'react';
import { Alert, Button, Checkbox, Dialog, DialogContent, FormControlLabel, Stack, Typography } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { deliveryApi } from '../../delivery/services/deliveryApi';
import { redeliveryApi } from '../services/opsApi';
import type { AuditRow, RedeliveryResult, RedeliveryTarget } from '../types/ops';

const label = (t: RedeliveryTarget) => `${t.deliveryType}${t.targetName ? `:${t.targetName}` : ''}${t.tableName ? `:${t.tableName}` : ''}`;

/**
 * 결과 재전송(관리자, docs/redelivery-design.md) — 전달 기록(DELIVERY)은 같은 대상(지금 설정)으로 다시, 응답·오류 기록은 그 인터페이스의
 * 지금 전달 대상 중 고른 곳으로(전달 조건은 보지 않는다). 한 번에 한 종류만.
 */
export function RedeliverDialog({ rows, onClose, onDone }: { rows: AuditRow[] | null; onClose: () => void; onDone: () => void }): ReactElement {
  const { t } = useI18n();
  const [targets, setTargets] = useState<RedeliveryTarget[]>([]);
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [result, setResult] = useState<RedeliveryResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const deliveries = !!rows?.length && rows.every((r) => r.messageType === 'DELIVERY');
  const responses = !!rows?.length && rows.every((r) => r.messageType === 'RESPONSE' || r.messageType === 'ERROR');

  useEffect(() => {
    setTargets([]); setChosen(new Set()); setResult(null); setError(null);
    if (!rows?.length || !responses) return;
    const names = new Set(rows.map((r) => r.name));
    deliveryApi.list().then((rules) => {
      const seen = new Map<string, RedeliveryTarget>();
      rules.filter((r) => names.has(r.interfaceName) && r.use).forEach((r) => {
        const tg = { deliveryType: r.deliveryType, targetName: r.targetName ?? null, tableName: r.tableName ?? null };
        seen.set(label(tg), tg);
      });
      setTargets([...seen.values()]);
    }).catch((e) => setError(extractApiError(e)));
  }, [rows, responses]);

  const send = async () => {
    if (!rows) return;
    setBusy(true); setError(null);
    try {
      const ids = rows.map((r) => r.id);
      setResult(deliveries ? await redeliveryApi.deliveries(ids)
        : await redeliveryApi.responses(ids, targets.filter((x) => chosen.has(label(x)))));
      onDone();
    } catch (e) {
      setError(extractApiError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={!!rows} onClose={onClose} maxWidth="sm" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.redeliver.title', '결과 재전송')} — {rows?.length ?? 0}{t('i18n.tc.redeliver.count', '건')}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={1.5}>
          {!deliveries && !responses && <Alert severity="warning">{t('i18n.tc.redeliver.mixed', '전달 기록(DELIVERY)만, 또는 응답·오류 기록만 고르세요.')}</Alert>}
          {deliveries && <Typography variant="body2">{t('i18n.tc.redeliver.deliveriesHelp', '전달 기록을 같은 대상으로 다시 보냅니다(대상 주소·테이블은 지금 설정).')}</Typography>}
          {responses && (
            <>
              <Typography variant="body2">{t('i18n.tc.redeliver.responsesHelp', '응답을 보낼 곳을 고르세요 — 그 인터페이스의 지금 전달 정의. 전달 조건(성공·실패)은 보지 않습니다.')}</Typography>
              {targets.length === 0 && <Alert severity="info">{t('i18n.tc.redeliver.noTargets', '이 인터페이스에 쓰는 전달 정의가 없습니다.')}</Alert>}
              {targets.map((x) => (
                <FormControlLabel key={label(x)} label={label(x)} control={(
                  <Checkbox checked={chosen.has(label(x))} onChange={(e) => {
                    const next = new Set(chosen);
                    if (e.target.checked) next.add(label(x)); else next.delete(label(x));
                    setChosen(next);
                  }} />
                )} />
              ))}
            </>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          {result && (
            <Alert severity={result.rejected.length ? 'warning' : 'success'}>
              {t('i18n.tc.redeliver.accepted', '보냄(접수)')} {result.accepted}
              {result.rejected.map((r) => <div key={`${r.auditId}-${r.target}`}>#{r.auditId} {r.target ?? ''}: {r.reason}</div>)}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}>
        <Button variant="contained" onClick={() => { void send(); }}
          disabled={busy || !!result || (!deliveries && !(responses && chosen.size > 0))}>
          {t('i18n.tc.redeliver.send', '다시 보내기')}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
