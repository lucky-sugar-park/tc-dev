import { bootstrap } from 'wow-mes-frontend-framework';
import { connectFrameworkI18n, publishBuildId } from 'wow-mes-frontend-shared';
import { LicenseInfo } from '@mui/x-license';
import { buildAppConfig } from './config';
import { registerTcIcons } from './shared/icons';
import { loadTcConfig } from './shared/tcConfig';

// MUI X Pro 라이선스 — 환경 변수로만(저장소에 적지 않는다, 결정 F7). 없으면 개발 워터마크
const muiXKey = import.meta.env.VITE_MUI_X_LICENSE_KEY as string | undefined;
if (muiXKey) LicenseInfo.setLicenseKey(muiXKey);

// framework(권한 없음 화면 등)가 shared 의 다국어를 읽게 한다 — bootstrap 앞에서 한 번
connectFrameworkI18n();
registerTcIcons();

declare const __APP_BUILD__: string;
publishBuildId(typeof __APP_BUILD__ === 'string' ? __APP_BUILD__ : undefined);

// 배포 설정(vplc 주소 등)을 읽은 뒤 띄운다 — 메뉴가 그 값에 따라 달라진다
void loadTcConfig().then(() => bootstrap(buildAppConfig()));
