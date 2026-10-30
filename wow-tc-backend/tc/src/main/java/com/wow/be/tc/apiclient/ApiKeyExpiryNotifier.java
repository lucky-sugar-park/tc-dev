package com.wow.be.tc.apiclient;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * API 키 만료 임박 알림 (설계 §6.9). 매일 08:00 에 남은 일수가 알림 일수(기본 30·7·1일) 이내인 활성 키를 찾아
 * WARN 로그와 SSE 이벤트({@value ApiKeyExpiringEvent#EVENT_KIND})로 알린다.
 *
 * <p>키가 만료되면 그 시스템의 PLC 요청이 끊기므로 놓치지 않게 알리는 것이 목적이다. 인스턴스가 여럿이면 각자 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyExpiryNotifier {

	private final ApiClientService apiClientService;

	private final ApiClientProperties properties;

	@Scheduled(cron = "${tc.api-client.expiry-check-cron:0 0 8 * * *}")
	public void checkExpiringKeys() {
		int maxDays = Arrays.stream(properties.getExpiryNoticeDays()).max().orElse(30);
		List<ApiClientKeyInfo> keys = apiClientService.findKeysExpiringWithin(maxDays);
		long now = System.currentTimeMillis();
		for (ApiClientKeyInfo key : keys) {
			long daysLeft = TimeUnit.MILLISECONDS.toDays(key.getExpiresAt().getTime() - now);
			int threshold = noticeThreshold(daysLeft);
			if (threshold < 0) {
				continue;
			}
			log.warn("API 키 만료 임박({}일 안): client={}, keyId={}, expiresAt={} — 새 키를 발급해 교체하세요",
				threshold, key.getClientId(), key.getKeyId(), key.getExpiresAt());
			push(key, daysLeft);
		}
	}

	/** 남은 일수가 들어가는 가장 작은 알림 일수. 없으면 -1 */
	private int noticeThreshold(long daysLeft) {
		return Arrays.stream(properties.getExpiryNoticeDays())
			.filter(d -> daysLeft < d)
			.min()
			.orElse(-1);
	}

	private void push(ApiClientKeyInfo key, long daysLeft) {
		try {
			ApiKeyExpiringEvent event = new ApiKeyExpiringEvent();
			event.setEventId(UUID.randomUUID().toString());
			event.setEventKind(ApiKeyExpiringEvent.EVENT_KIND);
			event.setData(new ApiKeyExpiringEvent.Payload(key.getClientId(), key.getKeyId(), key.getExpiresAt(), daysLeft));
			event.setComment("api key expiring");
			event.setReconnectTime(3000L);
			EventPusher.newPusher().push(event);
		} catch (RuntimeException e) {
			log.warn("API 키 만료 임박 알림을 화면에 보내지 못했습니다: keyId={}", key.getKeyId(), e);
		}
	}

	/** 화면 알림 이벤트 */
	public static class ApiKeyExpiringEvent extends PushEventPayload {

		public static final String EVENT_KIND = "apikey.expiring";

		public record Payload(String clientId, String keyId, Date expiresAt, long daysLeft) {
		}

		@Override
		public String toJson() {
			Payload p = (Payload) getData();
			return "{\"clientId\":\"" + p.clientId() + "\",\"keyId\":\"" + p.keyId() + "\",\"expiresAt\":"
				+ p.expiresAt().getTime() + ",\"daysLeft\":" + p.daysLeft() + "}";
		}
	}
}
