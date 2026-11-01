/**
 * 배포 때 바꾸는 값 — {@code /tc-config.json}(빌드 없이 폴더에서 고친다, docs/frontend-design.md §3). 읽지 못하면 기본값.
 */
export type TcConfig = {
  /** 가상 PLC(vplc) 주소 — 비우면 가상 PLC 메뉴를 숨긴다(운영 설치에는 vplc 가 없다, 결정 F8) */
  vplcUrl?: string;
};

let loaded: TcConfig = {};

export async function loadTcConfig(): Promise<TcConfig> {
  try {
    const res = await fetch('/tc-config.json', { cache: 'no-cache' });
    if (res.ok) loaded = (await res.json()) as TcConfig;
  } catch {
    // 없거나 깨졌으면 기본값
  }
  return loaded;
}

export const tcConfig = (): TcConfig => loaded;
