import { type ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

/** 목록 표 위 왼쪽의 제목 줄 — 글머리 막대·제목·(건수, 주면). 오른쪽에 둘 것(보기 전환 등)은 children 으로 */
export function ListTitle({ title, count, children }: { title: string; count?: number; children?: ReactNode }) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box sx={{ width: 4, height: 16, borderRadius: 0.5, bgcolor: 'primary.main', flexShrink: 0 }} />
      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
        {title}
        {count !== undefined && (
          <Typography component="span" variant="subtitle1" color="text.secondary" sx={{ ml: 0.75 }}>({count.toLocaleString()})</Typography>
        )}
      </Typography>
      {children && <><Box sx={{ flex: 1 }} />{children}</>}
    </Box>
  );
}
