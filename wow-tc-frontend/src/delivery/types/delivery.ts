export type DeliveryType = 'CALLBACK_URL' | 'WEBHOOK' | 'DB_TABLE';
export type DeliverOn = 'ALL' | 'SUCCESS' | 'FAILURE';

/** 결과 전달 정의 한 줄 — 백엔드 DeliveryDefinitionInfo */
export type DeliveryRule = {
  id?: string;
  interfaceName: string;
  deliveryType: DeliveryType;
  targetName?: string | null;
  tableName?: string | null;
  deliverOn: DeliverOn;
  onChangeOnly: boolean;
  use: boolean;
  description?: string;
  updatedBy?: string;
  updatedDate?: string;
};

export type WebHookInfo = {
  id?: number;
  name: string;
  url: string;
  status?: string;
  sampleData?: string;
  description?: string;
  updatedBy?: string;
  updatedDate?: string;
};

export type DbConnectionInfo = {
  name: string;
  jdbcUrl: string;
  username?: string;
  /** 보낼 때만 — 응답에는 없다(passwordSet 만) */
  password?: string;
  passwordSet?: boolean;
  maxPoolSize: number;
  use: boolean;
  description?: string;
  updatedBy?: string;
  updatedDate?: string;
};
