import { type ReactElement } from 'react';
import {
  Box, IconButton, MenuItem, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import { useI18n } from 'wow-mes-frontend-shared';
import { POINT_TYPES, isNumericType, type PointInfo, type PointType, type ProtocolArea, type ProtocolDescriptor } from '../types/plc';

/** Modicon 표기(40001 …)의 첫 숫자 → Modbus 영역 */
const MODICON: Record<string, string> = { '4': 'HR', '3': 'IR', '0': 'CO', '1': 'DI' };

/** 워드 안 비트 꼬리 — MELSEC D100.A(16진), Modbus HR1.15(10진). 범위 검사는 서버 */
const WORD_BIT = /\.[0-9A-F]{1,2}$/;

/** 워드 디바이스·레지스터 안의 비트 주소인지(docs/word-bit-points.md) */
export const isWordBitAddress = (address: string): boolean => WORD_BIT.test(address.trim().toUpperCase());

/** 주소가 어느 영역인가 — 가장 긴 접두어(D·DX·SD …), Modbus 는 Modicon 숫자도. 비트 꼬리(.3)는 떼고 본다 */
export function areaOf(address: string, protocol?: ProtocolDescriptor): ProtocolArea | undefined {
  if (!protocol || !address) return undefined;
  const a = address.trim().toUpperCase().replace(WORD_BIT, '');
  if (/^\d{5,6}$/.test(a)) return protocol.areas.find((x) => x.prefix === MODICON[a[0]]);
  return [...protocol.areas].sort((x, y) => y.prefix.length - x.prefix.length).find((x) => a.startsWith(x.prefix));
}

export const emptyPoint = (): PointInfo => ({ name: '', address: '', type: 'INT16', count: 1 });

/** 워드 안 비트면 BOOL 만, 아니면 영역의 형 */
function typesOf(address: string, area?: ProtocolArea): PointType[] {
  if (area && !area.bit && isWordBitAddress(address)) return ['BOOL'];
  return area?.types ?? POINT_TYPES;
}

/** 숫자 칸 글자 → 값(빈 칸은 null) */
const num = (s: string): number | null => (s.trim() === '' || Number.isNaN(Number(s)) ? null : Number(s));

/**
 * 점 표 편집 — 이름·주소·형·개수·길이(STRING·BYTES)·순서 재정의·배율·오프셋·단위·쓰기 범위·설명 (docs/frontend-design.md §5,
 * docs/interface-detail-design.md §9). 주소를 적으면 그 영역에서 쓸 수 있는 형만 고르게 한다 — 워드 안 비트(D100.3)는 BOOL
 * (검증은 서버 드라이버). {@code writeInterface} 면 읽기 전용 영역(Modbus IR·DI)과 워드 안 비트를 경고한다.
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
  // OPC UA 는 값의 형을 서버가 정한다 — 바이트·워드 순서가 없다(서버가 400)
  const noOrder = protocol?.protocol === 'OPC_UA';

  return (
    <Box>
      <Box sx={{ overflowX: 'auto' }}>
        <Table size="small" sx={{ '& td, & th': { px: 0.5, py: 0.25 }, minWidth: compact ? 640 : 1400 }}>
          <TableHead>
            <TableRow>
              <TableCell sx={{ width: 160 }}>{t('i18n.tc.point.name', '이름')}</TableCell>
              <TableCell sx={{ width: 140 }}>{t('i18n.tc.point.address', '주소')}</TableCell>
              <TableCell sx={{ width: 120 }}>{t('i18n.tc.point.type', '형')}</TableCell>
              <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.count', '개수')}</TableCell>
              <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.length', '길이')}</TableCell>
              {!compact && <TableCell sx={{ width: 100 }}>{t('i18n.tc.point.byteOrder', '바이트 순서')}</TableCell>}
              {!compact && <TableCell sx={{ width: 100 }}>{t('i18n.tc.point.wordOrder', '워드 순서')}</TableCell>}
              {!compact && <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.scale', '배율')}</TableCell>}
              {!compact && <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.offset', '오프셋')}</TableCell>}
              {!compact && <TableCell sx={{ width: 70 }}>{t('i18n.tc.point.unit', '단위')}</TableCell>}
              {!compact && <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.min', '최소')}</TableCell>}
              {!compact && <TableCell sx={{ width: 80 }}>{t('i18n.tc.point.max', '최대')}</TableCell>}
              {!compact && <TableCell>{t('i18n.tc.common.description', '설명')}</TableCell>}
              <TableCell sx={{ width: 110 }} />
            </TableRow>
          </TableHead>
          <TableBody>
            {points.map((p, i) => {
              const area = areaOf(p.address, protocol);
              const types = typesOf(p.address, area);
              const badType = area && !types.includes(p.type);
              const hasLength = p.type === 'STRING' || p.type === 'BYTES';
              const numeric = isNumericType(p.type);
              const warn = writeInterface && area?.readOnly ? t('i18n.tc.point.readOnlyArea', '읽기만 되는 영역')
                : writeInterface && isWordBitAddress(p.address) ? t('i18n.tc.point.wordBitReadOnly', '워드 안 비트는 읽기만') : undefined;
              return (
                <TableRow key={i}>
                  <TableCell>
                    <TextField size="small" fullWidth value={p.name} placeholder="d100"
                      onChange={(e) => set(i, { name: e.target.value })} slotProps={{ htmlInput: { 'aria-label': `point-name-${i}` } }} />
                  </TableCell>
                  <TableCell>
                    <Tooltip title={area ? `${area.label}${area.hexNumber ? ' · 16진 번호' : ''}` : (protocol?.examples.join(', ') ?? '')}>
                      <TextField size="small" fullWidth value={p.address} placeholder={protocol?.examples[0] ?? 'D100'}
                        error={!!warn || (!!p.address && !area && !!protocol?.areas.length)} helperText={warn}
                        onChange={(e) => {
                          const address = e.target.value;
                          const allowed = typesOf(address, areaOf(address, protocol));
                          // 영역이 바뀌어 지금 형을 못 쓰면 그 영역의 첫 형으로(워드 안 비트는 BOOL)
                          set(i, !allowed.includes(p.type) ? { address, type: allowed[0] } : { address });
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
                      <TextField select size="small" fullWidth disabled={noOrder} value={p.byteOrder ?? ''} onChange={(e) => set(i, { byteOrder: (e.target.value || null) as PointInfo['byteOrder'] })}>
                        {orders.map((o) => <MenuItem key={o} value={o}>{o || t('i18n.tc.point.connectorDefault', '커넥터 기본')}</MenuItem>)}
                      </TextField>
                    </TableCell>
                  )}
                  {!compact && (
                    <TableCell>
                      <TextField select size="small" fullWidth disabled={noOrder} value={p.wordOrder ?? ''} onChange={(e) => set(i, { wordOrder: (e.target.value || null) as PointInfo['wordOrder'] })}>
                        {orders.map((o) => <MenuItem key={o} value={o}>{o || t('i18n.tc.point.connectorDefault', '커넥터 기본')}</MenuItem>)}
                      </TextField>
                    </TableCell>
                  )}
                  {!compact && (['scale', 'offset'] as const).map((k) => (
                    <TableCell key={k}>
                      <TextField size="small" type="number" disabled={!numeric} value={numeric ? (p[k] ?? '') : ''}
                        placeholder={k === 'scale' ? '1' : '0'} onChange={(e) => set(i, { [k]: num(e.target.value) })}
                        slotProps={{ htmlInput: { 'aria-label': `point-${k}-${i}`, step: 'any' } }} />
                    </TableCell>
                  ))}
                  {!compact && (
                    <TableCell>
                      <TextField size="small" disabled={!numeric} value={numeric ? (p.unit ?? '') : ''} onChange={(e) => set(i, { unit: e.target.value || null })}
                        slotProps={{ htmlInput: { 'aria-label': `point-unit-${i}`, maxLength: 32 } }} />
                    </TableCell>
                  )}
                  {!compact && (['min', 'max'] as const).map((k) => (
                    <TableCell key={k}>
                      <TextField size="small" type="number" disabled={!numeric} value={numeric ? (p[k] ?? '') : ''}
                        onChange={(e) => set(i, { [k]: num(e.target.value) })}
                        slotProps={{ htmlInput: { 'aria-label': `point-${k}-${i}`, step: 'any' } }} />
                    </TableCell>
                  ))}
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
