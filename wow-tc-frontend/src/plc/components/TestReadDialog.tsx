import { useEffect, useState, type ReactElement } from 'react';
import { Alert, Box, Button, Chip, Dialog, DialogContent, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { DialogFooter, DialogTitleClose, DraggablePaper, extractApiError, useI18n } from 'wow-mes-frontend-shared';
import { connectorApi } from '../services/plcApi';
import { PointsEditor, areaOf } from './PointsEditor';
import type { PointInfo, ProtocolDescriptor, TestReadResult } from '../types/plc';

const show = (v: unknown): string => (typeof v === 'object' && v !== null ? JSON.stringify(v) : String(v));

/**
 * 시험 읽기 — 인터페이스 없이 점 몇 개를 적어 바로 읽는다(게시된 커넥터, 다중화면 주인 인스턴스로). 주소·형을 확인한 뒤 인터페이스를
 * 만든다.
 */
export function TestReadDialog({ name, protocol, onClose, initialPoints }: {
  name: string | null;
  protocol?: ProtocolDescriptor;
  onClose: () => void;
  initialPoints?: PointInfo[];
}): ReactElement {
  const { t } = useI18n();
  const [points, setPoints] = useState<PointInfo[]>([]);
  const [result, setResult] = useState<TestReadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!name) return;
    // 첫 점은 주소 예와 그 영역의 첫 형 — 영역 목록의 첫 것(MELSEC SM 은 비트)으로 형을 고르면 워드 주소와 맞지 않는다
    const address = protocol?.examples[0] ?? '';
    const type = areaOf(address, protocol)?.types[0] ?? 'INT16';
    setPoints(initialPoints?.length ? initialPoints : [{ name: 'v1', address, type, count: 1 }]);
    setResult(null);
    setError(null);
  }, [name, initialPoints, protocol]);

  const read = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(await connectorApi.testRead(name!, points.filter((p) => p.name && p.address)));
    } catch (e) {
      setResult(null);
      setError(extractApiError(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={!!name} onClose={onClose} maxWidth="lg" fullWidth PaperComponent={DraggablePaper}>
      <DialogTitleClose onClose={onClose}>{t('i18n.tc.testRead.title', '시험 읽기')} — {name}</DialogTitleClose>
      <DialogContent dividers>
        <Stack spacing={2}>
          <PointsEditor points={points} onChange={setPoints} protocol={protocol} compact />
          {error && <Alert severity="error">{error}</Alert>}
          {result && (
            <Box>
              <Stack direction="row" spacing={1} sx={{ mb: 1, alignItems: 'center' }}>
                <Chip size="small" color={result.plcStatus.code === 0 ? 'success' : 'error'}
                  label={`${result.plcStatus.protocol} ${result.plcStatus.code === 0 ? 'OK' : result.plcStatus.text}`} />
                <Typography variant="caption" color="text.secondary">{result.elapsedMs} ms</Typography>
              </Stack>
              <Table size="small">
                <TableHead><TableRow><TableCell>{t('i18n.tc.point.name', '이름')}</TableCell><TableCell>{t('i18n.tc.testRead.value', '값')}</TableCell></TableRow></TableHead>
                <TableBody>
                  {Object.entries(result.values).map(([k, v]) => (
                    <TableRow key={k}><TableCell>{k}</TableCell><TableCell sx={{ fontFamily: 'monospace' }}>{show(v)}</TableCell></TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogFooter onClose={onClose}>
        <Button variant="contained" onClick={() => { void read(); }} disabled={busy || !points.some((p) => p.name && p.address)}>
          {t('i18n.tc.testRead.read', '읽기')}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
