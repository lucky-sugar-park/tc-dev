package com.wow.be.tc.vplc.machine;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

/**
 * 연결·끊김을 연결 이력(DB)으로 — MELSEC 은 Netty 채널 이벤트가, Modbus·OPC UA 는 이것을 부른다. 알림 실패가 통신을 멈추지 않게 삼킨다.
 */
@Slf4j
public final class ConnectionEvents {

	private ConnectionEvents() {
	}

	public static void dispatch(String vplcId, int port, String clientIp, String channelId, boolean connected, String description) {
		ClientConnectionEvent cce = new ClientConnectionEvent();
		cce.setVplcId(vplcId);
		cce.setPort(port);
		cce.setChannelId(channelId);
		cce.setClientIpAddress(clientIp);
		cce.setConnOrDisconn(connected ? "CONNECTED" : "DISCONNECTED");
		cce.setTimestamp(new Date());
		cce.setDescription(description);
		try {
			ClientConnectionEventDispatcher.getDispatcher().dispatch(cce);
		} catch (RuntimeException e) {
			log.warn("연결 이력을 남기지 못했습니다: {} {} {} — {}", vplcId, port, cce.getConnOrDisconn(), e.toString());
		}
	}
}
