import { useEffect, useState, type ReactElement } from 'react';
import { Accordion, AccordionDetails, AccordionSummary, Alert, Chip, Dialog, DialogContent, Stack, TextField, Typography } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { auditApi } from '../services/opsApi';
import type { AuditRow } from '../types/ops';

export const TYPE_COLOR: Record<string, 'default' | 'primary' | 'success' | 'error' | 'warning' | 'info'> = {
  REQUEST: 'primary', RESPONSE: 'success', ERROR: 'error', DELIVERY: 'info',
};

/** 실패한 기록인가 — 응답 설명이 ERR_, 오류 행, OK 가 아닌 전달 */
export const isFailed = (r: AuditRow): boolean => r.messageType === 'ERROR'
  || (r.messageType === 'RESPONSE' && (r.description ?? '').startsWith('ERR'))
  || (r.messageType === 'DELIVERY' && !(r.description ?? '').startsWith('OK'));

/** 거래 하나의 기록을 차례로(요청 → 응답·오류 → 전달) — 기록마다 data JSON */
export function AuditDetailDialog({ transactionId, onClose }: { transactionId: string | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setRows([]); setError(null);
    if (!transactionId) return;
    auditApi.transaction(transactionId).then((p) => setRows([...p.items].sort((a, b) => a.id - b.id))).catch((e) => setError(extractApiError(e)));
  }, [transactionId]);

  return (
    <Dialog open={!!transactionId} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.audit.transaction', '거래')} — {transactionId}</DialogTitleClose>
      <DialogContent dividers>
        {error && <Alert severity="error">{error}</Alert>}
        {rows.map((r, i) => (
          <Accordion key={r.id} defaultExpanded={i < 2} disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip size="small" color={TYPE_COLOR[r.messageType] ?? 'default'} label={r.messageType} />
                <Typography variant="body2">{fmtTime(r.timestamp)}</Typography>
                <Typography variant="body2" color="text.secondary">{r.name} · {r.sender} → {r.receiver}</Typography>
                {r.description && <Typography variant="body2" sx={{ color: isFailed(r) ? 'error.main' : 'text.secondary' }}>{r.description}</Typography>}
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Typography variant="caption" color="text.secondary">id {r.id} · messageId {r.messageId} · {t('i18n.tc.audit.principal', '호출자')} {r.principal}</Typography>
              <TextField fullWidth multiline value={JSON.stringify(r.dataMap ?? {}, null, 2)} minRows={3} maxRows={18} sx={{ mt: 1 }}
                slotProps={{ input: { readOnly: true }, htmlInput: { style: { fontFamily: 'monospace', fontSize: 12 } } }} />
            </AccordionDetails>
          </Accordion>
        ))}
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
