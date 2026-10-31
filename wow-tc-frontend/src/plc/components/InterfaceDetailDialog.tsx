import { useEffect, useState, type ReactElement, type ReactNode } from 'react';
import {
  Alert, Box, Chip, Dialog, DialogContent, Divider, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { fmtTime } from '../../shared/format';
import { RequestPlanView } from './RequestPlanView';
import { interfaceApi } from '../services/plcApi';
import type { InterfaceInfo, PlcConnectorInfo } from '../types/plc';

function Field({ label, children }: { label: string; children: ReactNode }): ReactElement {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{label}</Typography>
      <Typography variant="body2" component="div">{children ?? '—'}</Typography>
    </Box>
  );
}

/**
 * 인터페이스 상세 보기(읽기 전용, docs/interface-detail-design.md §4) — 기본 정보, 커넥터(프로토콜·설정), 점, 요청 미리보기.
 * 조회 권한만 있으면 연다(수정은 수정 대화상자).
 */
export function InterfaceDetailDialog({ name, connectors, onClose }: {
  name: string | null;
  connectors: PlcConnectorInfo[];
  onClose: () => void;
}): ReactElement {
  const { t } = useI18n();
  const [info, setInfo] = useState<InterfaceInfo | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setInfo(null);
    setError(null);
    if (!name) return;
    let cancelled = false;
    interfaceApi.find(name)
      .then((r) => { if (!cancelled) setInfo(r); })
      .catch((e) => { if (!cancelled) setError(extractApiError(e)); });
    return () => { cancelled = true; };
  }, [name]);

  const connector = connectors.find((c) => c.name === info?.plcName);
  const yes = (b?: boolean) => (b ? 'Y' : 'N');

  return (
    <Dialog open={!!name} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.interface.detail', '인터페이스 상세')} — {name}</DialogTitleClose>
      <DialogContent dividers>
        {error && <Alert severity="error">{error}</Alert>}
        {info && (
          <Stack spacing={2}>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1.5 }}>
              <Field label={t('i18n.tc.interface.name', '이름(opcode)')}>{info.name}</Field>
              <Field label={t('i18n.tc.interface.ifType', '타입')}>{info.interfaceType}</Field>
              <Field label={t('i18n.tc.interface.use', '사용')}>{yes(info.use)}</Field>
              <Field label={t('i18n.tc.interface.sync', '동기')}>{yes(info.sync)}</Field>
              <Field label={t('i18n.tc.interface.reply', '응답')}>{yes(info.reply)}</Field>
              {info.interfaceType === 'WRITE' && <Field label={t('i18n.tc.interface.readBack', '쓴 뒤 다시 읽기')}>{yes(info.readBack)}</Field>}
              <Field label="WebHook">{(info.webHookNameList ?? []).join(', ') || '—'}</Field>
              <Field label={t('i18n.tc.common.updated', '고친 때')}>{fmtTime(info.updatedDate)}</Field>
            </Box>
            <Field label={t('i18n.tc.common.description', '설명')}>{info.description || '—'}</Field>
            <Divider />
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('i18n.tc.interface.plc', 'PLC 커넥터')}</Typography>
              {connector ? (
                <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
                  <Chip size="small" color="primary" label={`${connector.name} · ${connector.protocol}`} />
                  <Chip size="small" variant="outlined" label={`${connector.hostIp}:${(connector.ports ?? []).join(',')}`} />
                  <Chip size="small" color={connector.published ? 'primary' : 'default'} variant="outlined" label={connector.published ? 'PUBLISHED' : 'RELEASED'} />
                </Stack>
              ) : <Typography variant="body2" color="text.secondary">{info.plcName ?? '—'}</Typography>}
              <Typography variant="caption" color="text.secondary" component="div">
                {t('i18n.tc.interface.headerHelp', '프레임·국 번호 같은 헤더 값은 커넥터 설정에서 오고, 명령·주소·길이는 드라이버가 점에서 만듭니다')}
              </Typography>
              <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5, mt: 0.5, alignItems: 'center' }}>
                <Typography variant="caption">{t('i18n.tc.interface.ownSettings', '이 인터페이스의 설정')}:</Typography>
                {Object.keys(info.settings ?? {}).length === 0
                  ? <Typography variant="caption" color="text.secondary">{t('i18n.tc.interface.noOwnSettings', '없음 — 커넥터 설정 그대로')}</Typography>
                  : Object.entries(info.settings ?? {}).map(([k, val]) => <Chip key={k} size="small" color="secondary" variant="outlined" label={`${k}=${String(val)}`} />)}
              </Stack>
            </Box>
            <Divider />
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>{t('i18n.tc.interface.points', '점')} ({info.points?.length ?? 0})</Typography>
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small" aria-label="points" sx={{ '& td, & th': { px: 0.75, py: 0.5 } }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('i18n.tc.point.name', '이름')}</TableCell>
                      <TableCell>{t('i18n.tc.point.address', '주소')}</TableCell>
                      <TableCell>{t('i18n.tc.interface.ifType', '타입')}</TableCell>
                      <TableCell>{t('i18n.tc.point.count', '개수')}</TableCell>
                      <TableCell>{t('i18n.tc.point.length', '길이')}</TableCell>
                      <TableCell>{t('i18n.tc.point.byteOrder', '바이트 순서')}</TableCell>
                      <TableCell>{t('i18n.tc.point.wordOrder', '워드 순서')}</TableCell>
                      <TableCell>{t('i18n.tc.common.description', '설명')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {(info.points ?? []).map((p) => (
                      <TableRow key={p.name}>
                        <TableCell>{p.name}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{p.address}</TableCell>
                        <TableCell>{p.type}</TableCell>
                        <TableCell>{p.count ?? 1}</TableCell>
                        <TableCell>{p.length || ''}</TableCell>
                        <TableCell>{p.byteOrder ?? ''}</TableCell>
                        <TableCell>{p.wordOrder ?? ''}</TableCell>
                        <TableCell>{p.description ?? ''}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            </Box>
            <Divider />
            <RequestPlanView info={info} auto />
          </Stack>
        )}
      </DialogContent>
      <DialogFooter onClose={onClose} />
    </Dialog>
  );
}
