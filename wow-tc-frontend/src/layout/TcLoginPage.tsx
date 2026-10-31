import { useState, type FormEvent, type ReactElement } from 'react';
import { Alert, Box, Button, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { authApi, extractApiError, toSessionUser, useI18n, useI18nStore, usePermissionStore, useSessionStore } from 'wow-mes-frontend-shared';

const LANGS: { value: 'ko' | 'en' | 'ja' | 'zh'; label: string }[] = [
  { value: 'ko', label: '한국어' },
  { value: 'en', label: 'English' },
  { value: 'ja', label: '日本語' },
  { value: 'zh', label: '中文' },
];

/**
 * tc 로그인 (결정 F1) — shared {@code LoginPage} 와 같은 모양에서 회원가입·인증 메일·비밀번호 찾기를 뺐다(tc 에 없다). 로그인 실패가
 * 많으면 tc 가 잠시 받지 않는다(429) — 서버 문구를 그대로 보인다.
 */
export function TcLoginPage(): ReactElement {
  const { t } = useI18n();
  const lang = useI18nStore((s) => s.lang);
  const setLang = useI18nStore((s) => s.setLang);
  const setSession = useSessionStore((s) => s.setSession);
  const [id, setId] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await authApi.login({ id: id.trim(), password });
      setSession(res.accessToken, toSessionUser(res.userInfo));
      void usePermissionStore.getState().load();
    } catch (err) {
      setError(extractApiError(err) || t('i18n.auth.loginFailed', '로그인에 실패했습니다. 다시 시도해 주세요.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'background.default', p: 2 }}>
      <Paper elevation={3} sx={{ p: 4, width: 360, maxWidth: '100%' }} component="form" onSubmit={handleSubmit}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>{t('i18n.tc.app.title', 'WOW-TC')}</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {t('i18n.tc.app.subtitle', 'PLC 게이트웨이')} · {t('i18n.auth.loginNeeded', '로그인이 필요합니다')}
        </Typography>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label={t('i18n.auth.id', '아이디')} required autoFocus autoComplete="username" value={id} onChange={(e) => setId(e.target.value)} />
          <TextField label={t('i18n.auth.password', '비밀번호')} type="password" required autoComplete="current-password"
            value={password} onChange={(e) => setPassword(e.target.value)} />
          <Button type="submit" variant="contained" disabled={loading || !id.trim() || !password}>{t('i18n.auth.login', '로그인')}</Button>
          <TextField select size="small" label="Language" value={lang} onChange={(e) => setLang(e.target.value as typeof lang)}>
            {LANGS.map((l) => <MenuItem key={l.value} value={l.value}>{l.label}</MenuItem>)}
          </TextField>
        </Stack>
      </Paper>
    </Box>
  );
}
