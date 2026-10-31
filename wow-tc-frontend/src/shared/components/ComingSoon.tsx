import type { ReactElement } from 'react';
import { Box, Typography } from '@mui/material';
import ConstructionIcon from '@mui/icons-material/Construction';
import { PageContainer, useI18n } from 'wow-mes-frontend-shared';

/** 아직 만들지 않은 화면 — 메뉴·권한·경로는 먼저 열어 두고 화면을 차례로 채운다(docs/frontend-design.md F10) */
export function ComingSoon({ title }: { title: string }): ReactElement {
  const { t } = useI18n();
  return (
    <PageContainer>
      <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 1, color: 'text.secondary' }}>
        <ConstructionIcon sx={{ fontSize: 48 }} />
        <Typography variant="h6">{title}</Typography>
        <Typography variant="body2">{t('i18n.tc.comingSoon', '이 화면은 준비 중입니다.')}</Typography>
      </Box>
    </PageContainer>
  );
}
