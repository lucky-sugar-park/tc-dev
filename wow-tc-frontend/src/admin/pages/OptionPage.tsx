import { useCallback, useEffect, useState, type ReactElement, type ReactNode } from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, FormControlLabel, Radio, RadioGroup, Stack, Switch, Typography,
} from '@mui/material';
import { useIsActiveScreen } from 'wow-mes-frontend-framework';
import {
  CommandBarActions, LoadErrorState, PageContainer, ScreenAction, extractApiError, showConfirm, showToast, useI18n, useLoadFailure,
} from 'wow-mes-frontend-shared';
import { usePerm } from '../../shared/usePerm';
import { optionApi } from '../services/adminApi';

const SCREEN = 'tc.admin.option';

function OptionCard({ title, help, current, children }: { title: string; help: string; current: ReactNode; children: ReactNode }): ReactElement {
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{title}</Typography>
          {current}
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2, whiteSpace: 'pre-line' }}>{help}</Typography>
        {children}
      </CardContent>
    </Card>
  );
}

/**
 * 운영 옵션 (docs/frontend-design.md §5) — 재기동 없이 바꾸는 보안 옵션. 바꾸면 설정 변경 이력에 남는다. "설정값으로" 는
 * application.yml(환경 변수) 값으로 되돌린다. 다중화면 인스턴스마다 따로라 바꾼 인스턴스에만 적용된다(재기동하면 설정값).
 */
export default function OptionPage(): ReactElement {
  const { t } = useI18n();
  const perm = usePerm(SCREEN);
  const active = useIsActiveScreen();
  const [mode, setMode] = useState<string | null>(null);
  const [anyUser, setAnyUser] = useState<boolean | null>(null);
  const [loading, setLoading] = useState(false);
  const { error, fail, clear, retry } = useLoadFailure();
  const canChange = perm('U');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [m, a] = await Promise.all([optionApi.permissionMode(), optionApi.dispatchAnyUser()]);
      setMode(m);
      setAnyUser(a);
      clear();
    } catch (e) {
      fail(extractApiError(e), { hasData: false, retry: () => { void load(); } });
    } finally {
      setLoading(false);
    }
  }, [fail, clear]);
  useEffect(() => { if (active) void load(); }, [active, load]);

  const change = async (confirm: string | null, run: () => Promise<unknown>) => {
    if (confirm && !(await showConfirm({ message: confirm, severity: 'warning' }))) return;
    try {
      await run();
      showToast(t('i18n.common.saved', '저장했습니다'), 'success');
      await load();
    } catch (e) {
      showToast(extractApiError(e), 'error');
    }
  };

  if (error && mode == null) return <LoadErrorState message={error} onRetry={retry} retrying={loading} />;

  return (
    <PageContainer>
      <CommandBarActions>
        <ScreenAction kind="refresh" onClick={() => { void load(); }} />
      </CommandBarActions>
      <Box sx={{ overflow: 'auto', p: 1 }}>
        <Stack spacing={2} sx={{ maxWidth: 860 }}>
          <Alert severity="info">
            {t('i18n.tc.option.help', '재기동 없이 바로 바뀝니다. 다중화면 바꾼 인스턴스에만 적용되고, 재기동하면 설정값(application.yml)으로 돌아갑니다.')}
          </Alert>
          <OptionCard title={t('i18n.tc.option.permissionMode', '권한 검사 모드')}
            help={t('i18n.tc.option.permissionModeHelp', 'AUDIT — 권한이 없어도 막지 않고 기록만 남긴다(도입 초기).\nENFORCE — 권한이 없으면 403 으로 막는다.')}
            current={mode && <Chip size="small" color={mode === 'ENFORCE' ? 'success' : 'warning'} label={mode} />}>
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <RadioGroup row value={mode ?? ''} aria-label={t('i18n.tc.option.permissionMode', '권한 검사 모드')}
                onChange={(e) => {
                  const next = e.target.value;
                  void change(next === 'AUDIT' ? t('i18n.tc.option.confirmAudit', 'AUDIT 로 바꾸면 권한이 없는 요청도 통과합니다. 바꿀까요?') : null,
                    () => optionApi.setPermissionMode(next));
                }}>
                <FormControlLabel value="AUDIT" control={<Radio />} label="AUDIT" disabled={!canChange} />
                <FormControlLabel value="ENFORCE" control={<Radio />} label="ENFORCE" disabled={!canChange} />
              </RadioGroup>
              {canChange && <Button size="small" onClick={() => { void change(null, () => optionApi.setPermissionMode(null)); }}>{t('i18n.tc.option.reset', '설정값으로')}</Button>}
            </Stack>
          </OptionCard>
          <OptionCard title={t('i18n.tc.option.dispatchAnyUser', '기존 JWT 호출자 전환 스위치')}
            help={t('i18n.tc.option.dispatchAnyUserHelp', '켜면 로그인한 사용자 누구나 PLC 요청을 할 수 있다 — API 키로 옮기지 못한 시스템이 드러났을 때 잠시 켠다.\n전환이 끝나면 끈다.')}
            current={anyUser != null && <Chip size="small" color={anyUser ? 'warning' : 'default'} label={anyUser ? 'ON' : 'OFF'} />}>
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <FormControlLabel label={t('i18n.tc.option.on', '켜기')} disabled={!canChange || anyUser == null}
                control={(
                  <Switch checked={!!anyUser} slotProps={{ input: { 'aria-label': t('i18n.tc.option.dispatchAnyUser', '기존 JWT 호출자 전환 스위치') } }}
                    onChange={(e) => {
                      const on = e.target.checked;
                      void change(on ? t('i18n.tc.option.confirmAnyUser', '켜면 로그인한 사용자 누구나 PLC 요청을 할 수 있습니다. 켤까요?') : null,
                        () => optionApi.setDispatchAnyUser(on));
                    }} />
                )} />
              {canChange && <Button size="small" onClick={() => { void change(null, () => optionApi.setDispatchAnyUser(null)); }}>{t('i18n.tc.option.reset', '설정값으로')}</Button>}
            </Stack>
          </OptionCard>
        </Stack>
      </Box>
    </PageContainer>
  );
}
