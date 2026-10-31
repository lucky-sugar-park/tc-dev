import { useEffect, useState, type ReactElement } from 'react';
import {
  Alert, Box, Button, Chip, Dialog, DialogContent, Stack, Tab, Table, TableBody, TableCell, TableHead, TableRow, Tabs, Typography,
} from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, showConfirm, useI18n } from 'wow-mes-frontend-shared';
import { interfaceApi } from '../services/plcApi';
import type { InterfaceInfo, PointsConversionInfo, PropInfo } from '../types/plc';

function PropTable({ props }: { props?: PropInfo[] }): ReactElement {
  const { t } = useI18n();
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>{t('i18n.tc.raw.order', '순서')}</TableCell><TableCell>{t('i18n.tc.point.name', '이름')}</TableCell>
          <TableCell>{t('i18n.tc.point.type', '형')}</TableCell><TableCell>{t('i18n.tc.point.length', '길이')}</TableCell>
          <TableCell>{t('i18n.tc.raw.value', '값')}</TableCell><TableCell>{t('i18n.tc.common.description', '설명')}</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {[...(props ?? [])].sort((a, b) => a.order - b.order).map((p) => (
          <TableRow key={p.name}>
            <TableCell>{p.order}</TableCell><TableCell>{p.name}</TableCell><TableCell>{p.type}</TableCell><TableCell>{p.length}</TableCell>
            <TableCell sx={{ fontFamily: 'monospace' }}>{p.strValue ?? ''}</TableCell><TableCell>{p.description ?? ''}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

/**
 * RAW 인터페이스 보기(읽기 전용) + POINTS 로 변환 (docs/plc-protocol-design.md §15.2). 변환은 먼저 미리보기 — 만들 점·경고·옛 응답
 * 인터페이스를 보이고, 적용하면 같은 opcode 가 점 정의로 돈다(헤더·데이터 정의는 남아 되돌릴 수 있다).
 */
export function RawInterfaceDialog({ name, canConvert, onClose, onConverted }: {
  name: string | null;
  canConvert: boolean;
  onClose: () => void;
  onConverted: () => void;
}): ReactElement {
  const { t } = useI18n();
  const [info, setInfo] = useState<InterfaceInfo | null>(null);
  const [reply, setReply] = useState<InterfaceInfo | null>(null);
  const [tab, setTab] = useState(0);
  const [preview, setPreview] = useState<PointsConversionInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setInfo(null); setReply(null); setPreview(null); setError(null); setTab(0);
    if (!name) return;
    interfaceApi.find(name).then(async (i) => {
      setInfo(i);
      if (i.replyName) setReply(await interfaceApi.find(i.replyName).catch(() => null));
    }).catch((e) => setError(extractApiError(e)));
  }, [name]);

  const dryRun = async () => {
    setBusy(true); setError(null);
    try { setPreview(await interfaceApi.convert(name!, true)); setTab(3); } catch (e) { setPreview(null); setError(extractApiError(e)); } finally { setBusy(false); }
  };
  const apply = async () => {
    const ok = await showConfirm({
      message: t('i18n.tc.convert.confirm', '{name} 을(를) POINTS 로 바꿀까요? 같은 opcode·같은 data 키로 돈다. 되돌리려면 수정에서 mode 를 RAW 로.').replace('{name}', name!),
      severity: 'warning',
    });
    if (!ok) return;
    setBusy(true); setError(null);
    try { await interfaceApi.convert(name!, false); onConverted(); } catch (e) { setError(extractApiError(e)); } finally { setBusy(false); }
  };

  return (
    <Dialog open={!!name} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>
        {t('i18n.tc.raw.title', 'RAW 인터페이스')} — {name} {info && <Chip size="small" label={info.interfaceType} sx={{ ml: 1 }} />}
      </DialogTitleClose>
      <DialogContent dividers sx={{ minHeight: 360 }}>
        <Stack spacing={1}>
          {error && <Alert severity="error">{error}</Alert>}
          <Typography variant="body2" color="text.secondary">
            {t('i18n.tc.raw.help', 'MC 3E 프레임 바이트로 정의한 옛 인터페이스 — 여기서는 볼 수만 있다. POINTS 로 바꾸면 점 표로 고친다.')}
          </Typography>
          <Tabs value={tab} onChange={(_e, x) => setTab(x)}>
            <Tab label={t('i18n.tc.raw.header', '헤더')} />
            <Tab label={t('i18n.tc.raw.data', '데이터')} />
            <Tab label={`${t('i18n.tc.raw.reply', '응답 인터페이스')} ${info?.replyName ?? ''}`} disabled={!reply} />
            <Tab label={t('i18n.tc.convert.preview', '변환 미리보기')} disabled={!preview} />
          </Tabs>
          <Box sx={{ overflowX: 'auto' }}>
            {tab === 0 && <PropTable props={info?.headerProps} />}
            {tab === 1 && <PropTable props={info?.dataProps} />}
            {tab === 2 && <PropTable props={reply?.dataProps} />}
            {tab === 3 && preview && (
              <Stack spacing={1}>
                {preview.warnings.map((w) => <Alert key={w} severity="warning">{w}</Alert>)}
                {preview.settings && Object.keys(preview.settings).length > 0 && (
                  <Alert severity="info">
                    {t('i18n.tc.convert.settings', '인터페이스 설정으로 둘 값')}: {Object.entries(preview.settings).map(([k, val]) => `${k}=${String(val)}`).join(', ')}
                  </Alert>
                )}
                {preview.replyName && (
                  <Alert severity="info">
                    {t('i18n.tc.convert.reply', '옛 응답 인터페이스 {reply} 는 POINTS 가 쓰지 않는다.').replace('{reply}', preview.replyName)}
                    {preview.replyUsedBy.length ? ` ${t('i18n.tc.convert.replyUsed', '아직 쓰는 인터페이스')}: ${preview.replyUsedBy.join(', ')}` : ` ${t('i18n.tc.convert.replyFree', '쓰는 곳이 없어 지워도 된다.')}`}
                  </Alert>
                )}
                <Table size="small">
                  <TableHead><TableRow>
                    <TableCell>{t('i18n.tc.point.name', '이름')}</TableCell><TableCell>{t('i18n.tc.point.address', '주소')}</TableCell>
                    <TableCell>{t('i18n.tc.point.type', '형')}</TableCell><TableCell>{t('i18n.tc.point.length', '길이')}</TableCell>
                  </TableRow></TableHead>
                  <TableBody>
                    {preview.points.map((p) => (
                      <TableRow key={p.name}><TableCell>{p.name}</TableCell><TableCell>{p.address}</TableCell><TableCell>{p.type}</TableCell><TableCell>{p.length || ''}</TableCell></TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Stack>
            )}
          </Box>
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}>
        {canConvert && info && (info.interfaceType === 'READ' || info.interfaceType === 'WRITE') && (
          <>
            <Button onClick={() => { void dryRun(); }} disabled={busy}>{t('i18n.tc.convert.dryRun', 'POINTS 변환 미리보기')}</Button>
            <Button variant="contained" onClick={() => { void apply(); }} disabled={busy || !preview}>{t('i18n.tc.convert.apply', '변환 적용')}</Button>
          </>
        )}
      </DialogFooter>
    </Dialog>
  );
}
