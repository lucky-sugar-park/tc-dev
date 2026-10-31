package com.wow.be.tc.plc.connector;

import java.util.Date;

import com.wow.be.tc.Types.PlcConnectorOperStatus;

/**
 * 어댑터의 지금 상태 — 연결 상태 저장소가 주기적으로 읽어 Redis 에 남긴다(docs/plc-ha-design.md §3.4).
 *
 * @param lastError   마지막 연결 실패·끊김 사유(없으면 null)
 * @param lastErrorAt 그 시각
 */
public record AdapterStatus(PlcConnectorOperStatus operStatus, int activeChannels, String lastError, Date lastErrorAt) {
}
