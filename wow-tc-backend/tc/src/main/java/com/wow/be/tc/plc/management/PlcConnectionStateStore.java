package com.wow.be.tc.plc.management;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.wow.be.tc.plc.connector.AdapterStatus;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.connector.tcp.TcpPlcAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * PLC 연결 상태를 Redis 해시 {@code tc:plc-state:{커넥터 이름}} 에 둔다(docs/plc-ha-design.md §3.4, 2단계).
 *
 * <ul>
 *   <li>연결을 가진 인스턴스가 쓴다 — 연결 이벤트 때 바로, 그리고 {@code tc.plc.state.refresh-interval-ms}(5초)마다 올라와 있는
 *       어댑터 전부를. 만료({@code tc.plc.state.ttl-ms}, 15초)가 있어 인스턴스가 죽으면 그 상태는 사라진다(= 아무도 연결하지 않음).</li>
 *   <li>운영자 의도(게시·해제)는 DB {@code STATUS1} 이고 관리 API 만 바꾼다. 어댑터 이벤트는 더 이상 DB 상태를 바꾸지 않는다 —
 *       한 인스턴스의 연결 실패가 다른 인스턴스의 게시 상태를 바꾸지 않게.</li>
 *   <li>Redis 가 응답하지 않으면 쓰지 못하고 넘어간다(PLC 처리는 계속). 경고는 실패가 시작될 때와 회복될 때만 남긴다.</li>
 * </ul>
 */
@Slf4j
@Component
public class PlcConnectionStateStore {

	static final String KEY = "tc:plc-state:";

	private final StringRedisTemplate redis;

	private final InstanceIdentity identity;

	private final Duration ttl;

	private final AtomicBoolean failing = new AtomicBoolean();

	public PlcConnectionStateStore(RedisConnectionFactory connectionFactory, InstanceIdentity identity,
			@Value("${tc.plc.state.ttl-ms:15000}") long ttlMs) {
		this.redis = new StringRedisTemplate(connectionFactory);
		this.identity = identity;
		this.ttl = Duration.ofMillis(ttlMs);
	}

	/** 연결 이벤트 — 해제(SHUTDOWN·STOP)면 상태를 지우고, 아니면 이벤트를 남긴다 */
	public void onEvent(PlcConnectorEvent event) {
		String name = event.getPlcConnectorName();
		PlcConnectionPushEvent.push(event, identity.id());
		switch (event.getType()) {
			case SHUTDOWN, STOP -> write(() -> redis.delete(KEY + name));
			default -> {
				long at = (event.getTimestamp() != null ? event.getTimestamp() : new Date()).getTime();
				Map<String, String> fields = new LinkedHashMap<>();
				fields.put("lastEvent", event.getType().name());
				fields.put("lastEventAt", String.valueOf(at));
				fields.put("activeChannels", String.valueOf(event.getActiveChannelCount()));
				if (event.getOperStatus() != null) {
					fields.put("operStatus", event.getOperStatus().name());
				}
				switch (event.getType()) {
					case CONNECT -> fields.put("lastConnectedAt", String.valueOf(at));
					case DISCONNECT -> {
						fields.put("lastError", "연결 끊김 " + event.getRemoteIp() + ":" + event.getRemotePort()
							+ TcpPlcAdapter.describe(event.getCause()));
						fields.put("lastErrorAt", String.valueOf(at));
					}
					case START -> {
						if (event.getActiveChannelCount() > 0) {
							fields.put("lastConnectedAt", String.valueOf(at));
						}
					}
					default -> {
					}
				}
				save(name, fields);
			}
		}
	}

	/** 올라와 있는 어댑터의 상태를 다시 쓴다 — 만료 전에 갱신해 살아 있음을 알린다 */
	@Scheduled(fixedDelayString = "${tc.plc.state.refresh-interval-ms:5000}", initialDelayString = "${tc.plc.state.refresh-interval-ms:5000}")
	public void refresh() {
		for (ProgrammableControllerAdapter adapter : PlcAdapterManager.getPlcManager().adapters()) {
			if (!adapter.isLocal()) {
				continue;  // 원격 어댑터 — 상태는 연결한 인스턴스가 쓴다
			}
			AdapterStatus status = adapter.status();
			Map<String, String> fields = new LinkedHashMap<>();
			fields.put("operStatus", status.operStatus().name());
			fields.put("activeChannels", String.valueOf(status.activeChannels()));
			if (status.lastError() != null) {
				fields.put("lastError", status.lastError());
				fields.put("lastErrorAt", String.valueOf(status.lastErrorAt().getTime()));
			}
			save(adapter.getName(), fields);
		}
	}

	/** 커넥터 이름별 연결 상태. 상태가 없는(아무 인스턴스도 연결하지 않은) 이름은 빠진다. Redis 가 응답하지 않으면 빈 결과 */
	public Map<String, PlcConnectionState> find(Collection<String> names) {
		Map<String, PlcConnectionState> states = new LinkedHashMap<>();
		try {
			for (String name : names) {
				Map<Object, Object> hash = redis.opsForHash().entries(KEY + name);
				if (!hash.isEmpty()) {
					states.put(name, toState(name, hash));
				}
			}
			recovered();
		} catch (RuntimeException e) {
			failed("읽지", e);
		}
		return states;
	}

	private void save(String name, Map<String, String> fields) {
		fields.put("instance", identity.id());
		fields.put("updatedAt", String.valueOf(System.currentTimeMillis()));
		write(() -> {
			redis.opsForHash().putAll(KEY + name, fields);
			redis.expire(KEY + name, ttl);
		});
	}

	private void write(Runnable command) {
		try {
			command.run();
			recovered();
		} catch (RuntimeException e) {
			failed("남기지", e);
		}
	}

	private void failed(String what, RuntimeException e) {
		if (failing.compareAndSet(false, true)) {
			log.warn("PLC 연결 상태를 Redis 에 {} 못했습니다 — 회복될 때까지 이 경고는 다시 남기지 않습니다: {}", what, e.toString());
		}
	}

	private void recovered() {
		if (failing.compareAndSet(true, false)) {
			log.info("PLC 연결 상태 저장(Redis)이 회복되었습니다.");
		}
	}

	static PlcConnectionState toState(String name, Map<Object, Object> hash) {
		PlcConnectionState state = new PlcConnectionState();
		state.setPlcConnectorName(name);
		state.setInstance(text(hash.get("instance")));
		state.setOperStatus(text(hash.get("operStatus")));
		String channels = text(hash.get("activeChannels"));
		state.setActiveChannels(channels != null ? Integer.parseInt(channels) : 0);
		state.setLastEvent(text(hash.get("lastEvent")));
		state.setLastEventAt(date(hash.get("lastEventAt")));
		state.setLastConnectedAt(date(hash.get("lastConnectedAt")));
		state.setLastError(text(hash.get("lastError")));
		state.setLastErrorAt(date(hash.get("lastErrorAt")));
		state.setUpdatedAt(date(hash.get("updatedAt")));
		return state;
	}

	private static String text(Object value) {
		return value != null ? value.toString() : null;
	}

	private static Date date(Object value) {
		return value != null ? new Date(Long.parseLong(value.toString())) : null;
	}
}
