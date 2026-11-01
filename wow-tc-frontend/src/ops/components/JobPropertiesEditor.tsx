import { type ReactElement } from 'react';
import {
  Autocomplete, Box, Button, IconButton, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import PlaylistAddIcon from '@mui/icons-material/PlaylistAdd';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import { useI18n } from 'wow-mes-frontend-shared';
import type { InterfaceInfo } from '../../plc/types/plc';
import type { JobDetail, JobKind, JobPropertySpec } from '../types/ops';

/** 속성 값 형 — 백엔드 JobDataMap 이 문자열만 받아 이 셋만 둔다(int·long 은 저장할 때 검사) */
export const VALUE_TYPES = ['string', 'int', 'long'] as const;

const INTERFACE_NAME = 'interfaceName';

export const propDetail = (entryKey: string, entryValue = '', entryValueType = 'string', description = ''): JobDetail =>
  ({ entryKey, entryValue, entryValueType, description });

/** 종류의 속성 정의로 처음 속성 줄을 만든다(기본값을 채움) */
export function presetProperties(kind: JobKind | undefined): JobDetail[] {
  return (kind?.properties ?? []).map((p) => propDetail(p.key, p.defaultValue ?? '', p.valueType, p.description ?? ''));
}

/** 정의된 속성이 빠졌으면 앞에 채워 넣는다(저장된 Job 을 열 때) */
export function withSpecRows(rows: JobDetail[], kind: JobKind | undefined): JobDetail[] {
  const missing = (kind?.properties ?? []).filter((p) => !rows.some((r) => r.entryKey === p.key))
    .map((p) => propDetail(p.key, p.defaultValue ?? '', p.valueType, p.description ?? ''));
  return [...missing, ...rows.map((r) => ({ ...r, entryValueType: (r.entryValueType ?? 'string').toLowerCase() }))];
}

/** 화면에서 먼저 보는 오류(최종 검사는 서버) — 줄 번호 → 문구 */
export function propertyErrors(rows: JobDetail[], kind: JobKind | undefined, t: (k: string, d: string) => string): Record<number, string> {
  const errors: Record<number, string> = {};
  const seen = new Set<string>();
  rows.forEach((r, i) => {
    const key = r.entryKey.trim();
    const spec = kind?.properties.find((p) => p.key === key);
    if (!key) errors[i] = t('i18n.tc.schedule.prop.keyRequired', '키를 넣으세요');
    else if (seen.has(key)) errors[i] = t('i18n.tc.schedule.prop.keyDup', '키가 겹칩니다');
    else if (spec?.required && !r.entryValue.trim()) errors[i] = t('i18n.tc.common.required', '필수입니다');
    else if (r.entryValueType === 'int' || r.entryValueType === 'long') {
      if (!/^-?\d+$/.test(r.entryValue.trim())) errors[i] = t('i18n.tc.schedule.prop.notInteger', '정수가 아닙니다');
    }
    seen.add(key);
  });
  return errors;
}

/**
 * 스케줄 Job 속성 표 — Job 이 실행될 때 읽는 키·값(SCHEDULE_JOB_DETAIL). 종류의 정의된 속성은 키가 고정이고(필수는 지울 수 없다),
 * 종류가 허용하면(PLC 요청) 속성을 더한다 — WRITE 인터페이스면 점 이름마다 쓸 값. {@code readOnly} 면 보기만.
 */
export function JobPropertiesEditor({ rows, onChange, kind, interfaces, readOnly, errors = {} }: {
  rows: JobDetail[];
  onChange: (rows: JobDetail[]) => void;
  kind: JobKind | undefined;
  interfaces: InterfaceInfo[];
  readOnly?: boolean;
  errors?: Record<number, string>;
}): ReactElement {
  const { t } = useI18n();
  const specOf = (key: string): JobPropertySpec | undefined => kind?.properties.find((p) => p.key === key);
  const set = (i: number, patch: Partial<JobDetail>) => onChange(rows.map((r, k) => (k === i ? { ...r, ...patch } : r)));
  const iface = kind?.kind === 'PLC' ? interfaces.find((x) => x.name === rows.find((r) => r.entryKey === INTERFACE_NAME)?.entryValue) : undefined;
  const pointNames = iface?.interfaceType === 'WRITE' ? (iface.points ?? []).map((p) => p.name) : [];
  const unusedPoints = pointNames.filter((n) => !rows.some((r) => r.entryKey === n));
  const helpOf = (spec: JobPropertySpec) => t(`i18n.tc.schedule.propHelp.${spec.key}`, spec.description ?? '');

  const valueField = (r: JobDetail, i: number, spec?: JobPropertySpec): ReactElement => {
    const error = !!errors[i];
    if (spec?.key === INTERFACE_NAME && kind?.kind === 'PLC') {
      return (
        <Autocomplete size="small" options={interfaces.map((x) => x.name)} value={r.entryValue || null} disabled={readOnly}
          onChange={(_e, v) => set(i, { entryValue: v ?? '' })}
          renderOption={(p, o) => <li {...p} key={o}>{o} <Typography component="span" variant="caption" sx={{ ml: 1, color: 'text.secondary' }}>
            {interfaces.find((x) => x.name === o)?.interfaceType}</Typography></li>}
          renderInput={(p) => <TextField {...p} error={error} placeholder={t('i18n.tc.delivery.interface', '인터페이스(opcode)')}
            slotProps={{ ...p.slotProps, htmlInput: { ...p.slotProps.htmlInput, 'aria-label': `${r.entryKey} ${t('i18n.tc.schedule.prop.value', '값')}` } }} />} />
      );
    }
    if (spec && spec.options.length > 0) {
      return (
        <TextField select size="small" fullWidth value={r.entryValue} disabled={readOnly} error={error}
          onChange={(e) => set(i, { entryValue: e.target.value })} slotProps={{ htmlInput: { 'aria-label': `${r.entryKey} ${t('i18n.tc.schedule.prop.value', '값')}` } }}>
          {spec.options.map((o) => <MenuItem key={o} value={o}>{t(`i18n.tc.schedule.option.${o}`, o)}</MenuItem>)}
        </TextField>
      );
    }
    return (
      <TextField size="small" fullWidth value={r.entryValue} disabled={readOnly} error={error}
        onChange={(e) => set(i, { entryValue: e.target.value })} slotProps={{ htmlInput: { 'aria-label': `${r.entryKey || i} ${t('i18n.tc.schedule.prop.value', '값')}` } }} />
    );
  };

  return (
    <Box>
      <Table size="small" sx={{ '& td, & th': { px: 1, py: 0.5 } }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: '24%' }}>{t('i18n.tc.schedule.prop.key', '키')}</TableCell>
            <TableCell sx={{ width: '30%' }}>{t('i18n.tc.schedule.prop.value', '값')}</TableCell>
            <TableCell sx={{ width: 110 }}>{t('i18n.tc.schedule.prop.valueType', '값 형')}</TableCell>
            <TableCell>{t('i18n.tc.common.description', '설명')}</TableCell>
            {!readOnly && <TableCell sx={{ width: 44 }} />}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((r, i) => {
            const spec = specOf(r.entryKey);
            return (
              <TableRow key={i}>
                <TableCell>
                  {spec ? (
                    <Tooltip title={helpOf(spec)} placement="top-start" describeChild>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{r.entryKey}{spec.required && <Box component="span" sx={{ color: 'error.main' }}> *</Box>}</Typography>
                    </Tooltip>
                  ) : (
                    <Autocomplete size="small" freeSolo options={unusedPoints} value={r.entryKey} disabled={readOnly}
                      onInputChange={(_e, v) => set(i, { entryKey: v })}
                      renderInput={(p) => <TextField {...p} error={!!errors[i] && !r.entryKey.trim()}
                        slotProps={{ ...p.slotProps, htmlInput: { ...p.slotProps.htmlInput, 'aria-label': t('i18n.tc.schedule.prop.key', '키') } }} />} />
                  )}
                </TableCell>
                <TableCell>
                  {valueField(r, i, spec)}
                  {errors[i] && <Typography variant="caption" color="error">{errors[i]}</Typography>}
                </TableCell>
                <TableCell>
                  <TextField select size="small" fullWidth value={r.entryValueType ?? 'string'} disabled={readOnly || !!spec}
                    onChange={(e) => set(i, { entryValueType: e.target.value })}>
                    {VALUE_TYPES.map((v) => <MenuItem key={v} value={v}>{v}</MenuItem>)}
                  </TextField>
                </TableCell>
                <TableCell>
                  <TextField size="small" fullWidth value={r.description ?? ''} disabled={readOnly}
                    placeholder={spec ? helpOf(spec) : ''} onChange={(e) => set(i, { description: e.target.value })} />
                </TableCell>
                {!readOnly && (
                  <TableCell>
                    {!spec?.required && (
                      <IconButton size="small" aria-label={t('i18n.common.delete', '삭제')} onClick={() => onChange(rows.filter((_, k) => k !== i))}>
                        <DeleteOutlineIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                )}
              </TableRow>
            );
          })}
          {rows.length === 0 && (
            <TableRow><TableCell colSpan={5}><Typography variant="body2" color="text.secondary">{t('i18n.tc.schedule.prop.none', '속성이 없습니다')}</Typography></TableCell></TableRow>
          )}
        </TableBody>
      </Table>
      {!readOnly && kind?.extraProperties && (
        <Stack direction="row" spacing={1} sx={{ mt: 1, alignItems: 'center' }}>
          <Button size="small" startIcon={<AddIcon />} onClick={() => onChange([...rows, propDetail('')])}>{t('i18n.tc.schedule.prop.add', '속성 추가')}</Button>
          {unusedPoints.length > 0 && (
            <Button size="small" startIcon={<PlaylistAddIcon />}
              onClick={() => onChange([...rows, ...unusedPoints.map((n) => propDetail(n, '', 'int', iface?.points?.find((p) => p.name === n)?.description ?? ''))])}>
              {t('i18n.tc.schedule.prop.fillPoints', '쓸 점 채우기')} ({unusedPoints.length})
            </Button>
          )}
          {kind.kind === 'PLC' && (
            <Typography variant="caption" color="text.secondary">
              {iface?.interfaceType === 'WRITE'
                ? t('i18n.tc.schedule.prop.writeHelp', 'WRITE 인터페이스 — 점 이름마다 쓸 값을 둔다(모든 점)')
                : t('i18n.tc.schedule.prop.readHelp', 'READ 인터페이스는 interfaceName 만 둔다')}
            </Typography>
          )}
        </Stack>
      )}
    </Box>
  );
}
