import { registerIcon } from 'wow-mes-frontend-shared';
import SettingsInputComponentIcon from '@mui/icons-material/SettingsInputComponent';
import CableIcon from '@mui/icons-material/Cable';
import ScienceIcon from '@mui/icons-material/Science';
import SendIcon from '@mui/icons-material/Send';
import WebhookIcon from '@mui/icons-material/Webhook';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import ScheduleIcon from '@mui/icons-material/Schedule';
import HistoryIcon from '@mui/icons-material/History';
import VpnKeyIcon from '@mui/icons-material/VpnKey';
import TranslateIcon from '@mui/icons-material/Translate';
import MemoryIcon from '@mui/icons-material/Memory';
import DeveloperBoardIcon from '@mui/icons-material/DeveloperBoard';

/** tc 메뉴 아이콘 — shared 아이콘 표에 없는 것만 더한다(home·admin·users·settings·monitoring·analytics·datasource·commonCode 는 shared 것) */
export function registerTcIcons(): void {
  registerIcon('plc', (p) => <SettingsInputComponentIcon {...p} />);
  registerIcon('interface', (p) => <CableIcon {...p} />);
  registerIcon('test', (p) => <ScienceIcon {...p} />);
  registerIcon('delivery', (p) => <SendIcon {...p} />);
  registerIcon('webhook', (p) => <WebhookIcon {...p} />);
  registerIcon('log', (p) => <ReceiptLongIcon {...p} />);
  registerIcon('schedule', (p) => <ScheduleIcon {...p} />);
  registerIcon('history', (p) => <HistoryIcon {...p} />);
  registerIcon('key', (p) => <VpnKeyIcon {...p} />);
  registerIcon('translate', (p) => <TranslateIcon {...p} />);
  registerIcon('vplc', (p) => <DeveloperBoardIcon {...p} />);
  registerIcon('memory', (p) => <MemoryIcon {...p} />);
}
