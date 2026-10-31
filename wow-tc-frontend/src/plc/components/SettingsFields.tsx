import { type ReactElement } from 'react';
import { Box, MenuItem, TextField } from '@mui/material';
import type { ProtocolSetting } from '../types/plc';

/** 설정 값 → 입력 칸 글자(16진 항목은 0x…) */
export const settingText = (v: unknown, s?: ProtocolSetting): string => {
  if (v === null || v === undefined) return '';
  if (s?.hex && typeof v === 'number' && v > 9) return `0x${v.toString(16).toUpperCase()}`;
  return String(v);
};

/** 입력 칸 글자 → 보낼 설정(빈 칸은 뺀다 — 서버 기본값·커넥터 값). 숫자 항목의 10진 글자는 숫자로 */
export function settingsFromText(schema: ProtocolSetting[], values: Record<string, string>): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const s of schema) {
    const v = values[s.key]?.trim();
    if (v) out[s.key] = s.type === 'NUMBER' && /^-?\d+$/.test(v) ? Number(v) : v;
  }
  return out;
}

/**
 * 프로토콜 설명의 설정 목록으로 그린 입력 칸 — 커넥터 설정과 인터페이스 settings 가 함께 쓴다(docs/interface-detail-design.md §3).
 * 빈 칸은 {@code fallback} 값을 쓴다는 뜻이다(커넥터 폼은 서버 기본값, 인터페이스 폼은 커넥터 값).
 */
export function SettingsFields({ schema, values, onChange, fallbackLabel }: {
  schema: ProtocolSetting[];
  values: Record<string, string>;
  onChange: (values: Record<string, string>) => void;
  /** 빈 칸이 쓸 값의 설명 — 예 "기본 0x10", "커넥터 값 1" */
  fallbackLabel: (s: ProtocolSetting) => string;
}): ReactElement {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '1fr 1fr 1fr' }, gap: 2 }}>
      {schema.map((s) => (
        s.type === 'ENUM' ? (
          <TextField key={s.key} select label={s.key} value={values[s.key] ?? ''} helperText={s.description}
            onChange={(e) => onChange({ ...values, [s.key]: e.target.value })}
            slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }}>
            <MenuItem value="">{fallbackLabel(s)}</MenuItem>
            {(s.options ?? []).map((o) => <MenuItem key={o} value={o}>{o}</MenuItem>)}
          </TextField>
        ) : (
          <TextField key={s.key} label={s.key} value={values[s.key] ?? ''} helperText={s.description}
            placeholder={`${fallbackLabel(s)}${s.min != null ? ` (${s.min}~${s.max})` : ''}`}
            onChange={(e) => onChange({ ...values, [s.key]: e.target.value })} slotProps={{ inputLabel: { shrink: true } }} />
        )
      ))}
    </Box>
  );
}
