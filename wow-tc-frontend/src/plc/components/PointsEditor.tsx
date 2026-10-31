import { type ReactElement } from 'react';
import {
  Box, IconButton, MenuItem, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import { useI18n } from 'wow-mes-frontend-shared';
import { POINT_TYPES, type PointInfo, type PointType, type ProtocolArea, type ProtocolDescriptor } from '../types/plc';

/** Modicon 표기(40001 …)의 첫 숫자 → Modbus 영역 */
const MODICON: Record<string, string> = { '4': 'HR', '3': 'IR', '0': 'CO', '1': 'DI' };

/** 주소가 어느 영역인가 — 가장 긴 접두어(D·DX·SD …), Modbus 는 Modicon 숫자도 */
export function areaOf(address: string, protocol?: ProtocolDescriptor): ProtocolArea | undefined {
  if (!protocol || !address) return undefined;
  const a = address.trim().toUpperCase();
  if (/^\d{5,6}$/.test(a)) return protocol.areas.find((x) => x.prefix === MODICON[a[0]]);
  return [...protocol.areas].sort((x, y) => y.prefix.length - x.prefix.length).find((x) => a.startsWith(x.prefix));
}

export const emptyPoint = (): PointInfo => ({ name: '', address: '', type: 'INT16', count: 1 });

/**
 * 점 표 편집 — 이름·주소·형·개수·길이(STRING·BYTES)·순서 재정의·설명 (docs/frontend-design.md §5). 주소를 적으면 그 영역에서 쓸 수 있는 형만
 * 고르게 한다(검증은 서버 드라이버). {@code readOnlyWrite} 면 읽기 전용 영역(Modbus IR·DI)을 경고한다.
 */
export function PointsEditor({ points, onChange, protocol, writeInterface, compact }: {
  points: PointInfo[];
  onChange: (points: PointInfo[]) => void;
  protocol?: ProtocolDescriptor;
  /** 쓰기 인터페이스 — 읽기 전용 영역을 표시 */
  writeInterface?: boolean;
  /** 순서 재정의·설명 칸을 숨긴다(시험 읽기) */
  compact?: boolean;
}): ReactElement {
  const { t } = useI18n();
  const set = (i: number, patch: Partial<PointInfo>) => onChange(points.map((p, k) => (k === i ? { ...p, ...patch } : p)));
  const move = (i: number, d: -1 | 1) => {
    const j = i + d;
    if (j < 0 || j >= points.length) return;
    const next = [...points];
    [next[i], next[j]] = [next[j], next[i]];
    onChange(next);
  };
  const orders = ['', 'BIG', 'LITTLE'];

  return (
    <Box>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small" sx={{ '& td, & th': { px: 0.5, py: 0.25 }, minWidth: compact ? 640 : 980 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 160 }}>{t('i18n.tc.point.name', '이름')}</TableCell>
              <TableCell sx={{ width: 140 }}>{t('i18n.tc.point.address', '주소')}</TableCell>
              <TableCell sx={{ width: 120 }}>{t('i18n.tc.point.type', '형')}</TableCell>
              <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.count', '개수')}</TableCell>
              <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.length', '길이')}</TableCell>
              {!compact && <TableCell sx={{ width: 100 }}>{t('i18n.tc.point.byteOrder', '바이트 순서')}</TableCell>}
              {!compact && <TableCell sx={{ width: 100 }}>{t('i18n.tc.point.wordOrder', '워드 순서')}</TableCell>}
              {!compact && <TableCell>{t('i18n.tc.common.description', '설명')}</TableCell>}
              <TableCell sx={{ width: 110 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {points.map((p, i) => {
              const area = areaOf(p.address, protocol);
              const types = area?.types ?? POINT_TYPES;
              const badType = area && !area.types.includes(p.type);
              const hasLength = p.type === 'STRING' || p.type === 'BYTES';
              const warn = writeInterface && area?.readOnly ? t('i18n.tc.point.readOnlyArea', '읽기만 되는 영역') : undefined;
              return (
                <TableRow key={i}>
                  <TableCell>
                    <TextField size="small" fullWidth value={p.name} placeholder="d100"
                      onChange={(e) => set(i, { name: e.target.value })} slotProps={{ htmlInput: { 'aria-label': `point-name-${i}` } }} />
                  </TableCell>
                  <TableCell>
                    <Tooltip title={area ? `${area.label}${area.hexNumber ? ' · 16진 번호' : ''}` : (protocol?.examples.join(', ') ?? '')}>
                      <TextField size="small" fullWidth value={p.address} placeholder={protocol?.examples[0] ?? 'D100'}
                        error={!!warn || (!!p.address && !area)} helperText={warn}
                        onChange={(e) => {
                          const address = e.target.value;
                          const a = areaOf(address, protocol);
                          // 영역이 바뀌어 지금 형을 못 쓰면 그 영역의 첫 형으로
                          set(i, a && !a.types.includes(p.type) ? { address, type: a.types[0] } : { address });
                        }}
                        slotProps={{ htmlInput: { 'aria-label': `point-address-${i}` } }} />
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <TextField select size="small" fullWidth value={p.type} error={badType}
                      onChange={(e) => set(i, { type: e.target.value as PointType, length: (e.target.value === 'STRING' || e.target.value === 'BYTES') ? (p.length || 2) : undefined })}>
                      {types.map((ty) => <MenuItem key={ty} value={ty}>{ty}</MenuItem>)}
                      {!types.includes(p.type) && <MenuItem value={p.type}>{p.type}</MenuItem>}
                    </TextField>
                  </TableCell>
                  <TableCell>
                    <TextField size="small" type="number" value={p.count ?? 1}
                      onChange={(e) => set(i, { count: Math.max(1, Number(e.target.value) || 1) })} slotProps={{ htmlInput: { min: 1 } }} />
                  </TableCell>
                  <TableCell>
                    <TextField size="small" type="number" disabled={!hasLength} value={hasLength ? (p.length ?? '') : ''}
                      onChange={(e) => set(i, { length: Number(e.target.value) || undefined })} slotProps={{ htmlInput: { min: 1 } }} />
                  </TableCell>
                  {!compact && (
                    <TableCell>
                      <TextField select size="small" fullWidth value={p.byteOrder ?? ''} onChange={(e) => set(i, { byteOrder: (e.target.value || null) as PointInfo['byteOrder'] })}>
                        {orders.map((o) => <MenuItem key={o} value={o}>{o || t('i18n.tc.point.connectorDefault', '커넥터 기본')}</MenuItem>)}
                      </TextField>
                    </TableCell>
                  )}
                  {!compact && (
                    <TableCell>
                      <TextField select size="small" fullWidth value={p.wordOrder ?? ''} onChange={(e) => set(i, { wordOrder: (e.target.value || null) as PointInfo['wordOrder'] })}>
                        {orders.map((o) => <MenuItem key={o} value={o}>{o || t('i18n.tc.point.connectorDefault', '커넥터 기본')}</MenuItem>)}
                      </TextField>
                    </TableCell>
                  )}
                  {!compact && (
                    <TableCell>
                      <TextField size="small" fullWidth value={p.description ?? ''} onChange={(e) => set(i, { description: e.target.value })} />
                    </TableCell>
                  )}
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <IconButton size="small" onClick={() => move(i, -1)} disabled={i === 0} aria-label="up"><ArrowUpwardIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => move(i, 1)} disabled={i === points.length - 1} aria-label="down"><ArrowDownwardIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => onChange(points.filter((_, k) => k !== i))} aria-label={t('i18n.common.delete', '삭제')}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
        <IconButton size="small" color="primary" onClick={() => onChange([...points, emptyPoint()])} aria-label={t('i18n.tc.point.add', '점 추가')}>
          <AddIcon fontSize="small" />
        </IconButton>
        <Typography variant="caption" color="text.secondary">
          {t('i18n.tc.point.addressHelp', '주소 예')}: {protocol?.examples.join(' · ') ?? ''}
        </Typography>
      </Box>
    </Box>
  );
}
