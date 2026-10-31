package com.wow.be.tc.plc.management;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * PLC 연결 이벤트를 화면에 알리는 SSE 이벤트({@code plc_connection}, docs/frontend-design.md §4.1·F2). 연결 모니터·커넥터 목록이
 * 받아 바로 고친다. 이벤트는 PLC 를 연결한 인스턴스에서만 난다 — 다른 인스턴스에 붙은 화면은 연결 상태 조회(5초 갱신)로 따라온다.
 */
@Slf4j
public class PlcConnectionPushEvent extends PushEventPayload {

	public static final String EVENT_KIND = "plc_connection";

	private static final ObjectMapper JSON = new ObjectMapper();

	@Override
	public String toJson() {
		try {
			return JSON.writeValueAsString(getData());
		} catch (JsonProcessingException e) {
			return "{}";
		}
	}

	static void push(PlcConnectorEvent event, String instance) {
		try {
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("name", event.getPlcConnectorName());
			data.put("type", event.getType() != null ? event.getType().name() : null);
			data.put("operStatus", event.getOperStatus() != null ? event.getOperStatus().name() : null);
			data.put("activeChannels", event.getActiveChannelCount());
			data.put("cause", event.getCause());
			data.put("remote", event.getRemoteIp() != null ? event.getRemoteIp() + ":" + event.getRemotePort() : null);
			data.put("instance", instance);
			data.put("at", event.getTimestamp() != null ? event.getTimestamp().getTime() : System.currentTimeMillis());
			PlcConnectionPushEvent payload = new PlcConnectionPushEvent();
			payload.setEventId(UUID.randomUUID().toString());
			payload.setEventKind(EVENT_KIND);
			payload.setData(data);
			payload.setComment("plc connection");
			payload.setReconnectTime(3000L);
			EventPusher.newPusher().push(payload);
		} catch (RuntimeException e) {
			log.debug("PLC 연결 이벤트를 화면에 보내지 못했습니다: {}", e.toString());
		}
	}
}
