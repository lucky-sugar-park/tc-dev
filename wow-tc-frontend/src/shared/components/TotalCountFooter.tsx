import { type ReactElement } from 'react';
import { Box, Typography } from '@mui/material';
import { useI18n } from 'wow-mes-frontend-shared';

// 표 바닥줄 슬롯에 넘기는 값의 형(slotProps.footer)
declare module '@mui/x-data-grid-pro' {
  interface FooterPropsOverrides {
    total?: number;
  }
}

/** 표 오른쪽 아래 "총 N건" — mes-dev MDM 의 같은 이름 부품을 옮겼다(shared 가 내보내지 않는다) */
export function TotalCountFooter({ total = 0 }: { total?: number }): ReactElement {
  const { t } = useI18n();
  return (
    <Box sx={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', px: 2, py: 0.5, borderTop: 1, borderColor: 'divider' }}>
      <Typography variant="body2" color="text.secondary">
        {t('i18n.grid.footer.totalRows', '총 {n}건').replace('{n}', (total ?? 0).toLocaleString())}
      </Typography>
    </Box>
  );
}
