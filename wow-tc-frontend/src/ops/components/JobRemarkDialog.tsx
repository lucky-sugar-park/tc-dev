import { useState, type ReactElement } from 'react';
import { Button, Dialog, DialogContent, Typography } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import type { JobHistory } from '../types/ops';
import { AuditDetailDialog } from './AuditDetailDialog';

/** PLC 요청 Job 의 비고에 든 거래 ID — 송수신 기록으로 이어 본다 */
const trxOf = (remark?: string): string | null => /\(trx ([^)\s]+)\)/.exec(remark ?? '')?.[1] ?? null;

/** 실행 이력 한 행의 비고 전체 — PLC 요청이면 그 거래의 송수신 기록 */
export function JobRemarkDialog({ history, onClose }: { history: JobHistory | null; onClose: () => void }): ReactElement {
  const { t } = useI18n();
  const [trx, setTrx] = useState<string | null>(null);
  const transactionId = trxOf(history?.remark);
  return (
    <>
      <Dialog open={!!history} onClose={onClose} maxWidth="md" fullWidth PaperComponent={DraggablePaper}>
        <DialogTitleClose onClose={onClose}>
          {t('i18n.tc.schedule.remark', '비고')} — {history?.jobName} {fmtTime(history?.executionTime)} {history?.executionResult}
        </DialogTitleClose>
        <DialogContent dividers>
          <Typography component="pre" variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all', fontFamily: 'monospace', m: 0 }}>{history?.remark}</Typography>
        </DialogContent>
        <DialogFooter onClose={onClose}>
          {transactionId && <Button onClick={() => setTrx(transactionId)}>{t('i18n.tc.schedule.openAudit', '송수신 기록 보기')}</Button>}
        </DialogFooter>
      </Dialog>
      <AuditDetailDialog transactionId={trx} onClose={() => setTrx(null)} />
    </>
  );
}
