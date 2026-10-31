package com.wow.be.tc.plc.connector.opcua;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.SessionActivityListener;
import org.eclipse.milo.opcua.sdk.client.UaSession;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.connector.AdapterStatus;
import com.wow.be.tc.plc.connector.PlcConnectionEventListener;
import com.wow.be.tc.plc.connector.PlcConnectorEvent;
import com.wow.be.tc.plc.connector.PlcConnectorEvent.EventType;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.opcua.OpcUaDriver;
import com.wow.be.tc.plc.protocol.opcua.OpcUaSessions;

import lombok.extern.slf4j.Slf4j;

/**
 * OPC UA 커넥터 어댑터 (docs/opcua-design.md). Milo 클라이언트(세션) 하나를 들고 작업을 {@link OpcUaDriver#run} 으로 실행한다.
 *
 * <ul>
 *   <li>처음 연결은 게시 API 면 실패를 바로 알리고, 기동·소유권(백그라운드)이면 뒤에서 다시 시도한다(점점 긴 간격, 최대 60초).</li>
 *   <li>한 번 연결된 뒤 끊기면 Milo 가 세션을 다시 만든다 — 세션 활성·비활성을 연결 이벤트(CONNECT·DISCONNECT)로 남긴다(O11).</li>
 *   <li>구독(SUBSCRIBE 인터페이스)은 {@code OpcUaSubscriptionService} 가 이 어댑터의 클라이언트로 맞춘다 — {@link #sessionGeneration()} 이
 *       바뀌면(세션을 새로 만들면) 구독을 다시 만든다.</li>
 * </ul>
 * 프레임 교환({@link #exchangeFrame})은 없다 — 원격 인스턴스도 작업 단위({@code /execute})로 넘긴다.
 */
@Slf4j
public class OpcUaAdapter implements ProgrammableControllerAdapter {

	private long id;

	private String name;

	private String endpointUrl;

	private String hostIp;

	private Map<String, Object> settings = Map.of();

	private PlcConnectionEventListener listener;

	private boolean startEvenIfDisconnected;

	private volatile OpcUaClient client;

	private volatile boolean connected;

	private volatile boolean running;

	private volatile boolean quiet;

	private volatile PlcConnectorOperStatus operStatus = PlcConnectorOperStatus.STOPPED;

	private volatile String lastError;

	private volatile Date lastErrorAt;

	private final AtomicInteger generation = new AtomicInteger();

	private ScheduledExecutorService retry;

	private long retryDelayMs = 1000;

	@Override
	public long getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	/** 지금 세션의 클라이언트(연결 안 됐으면 null) — 구독 서비스용 */
	public OpcUaClient client() {
		return connected ? client : null;
	}

	/** 세션이 새로 활성화될 때마다 하나씩 는다 */
	public int sessionGeneration() {
		return generation.get();
	}

	public Map<String, Object> settings() {
		return settings;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void ready(long id, String name, Properties props) throws ToolControlException {
		log.info("OPC UA 어댑터 준비: id={}, name={}", id, name);
		this.id = id;
		this.name = name;
		this.hostIp = props.getProperty("hostIp");
		List ports = (List) props.get("targetPorts");
		Integer port = ports != null && !ports.isEmpty() && ports.get(0) instanceof Number n ? n.intValue() : null;
		if (props.get("settings") instanceof Map<?, ?> s) {
			this.settings = (Map<String, Object>) s;
		}
		this.endpointUrl = OpcUaSessions.endpointUrl(hostIp, port, settings);
		this.listener = (PlcConnectionEventListener) props.get("connectionListener");
		this.startEvenIfDisconnected = Boolean.TRUE.equals(props.get("startEvenIfDisconnected"));
		start();
	}

	@Override
	public synchronized void start() throws ToolControlException {
		running = true;
		quiet = false;
		operStatus = PlcConnectorOperStatus.RUNNING;
		retry = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "opcua-" + name + "-retry");
			t.setDaemon(true);
			return t;
		});
		emit(EventType.START, "START", endpointUrl);
		if (!tryConnect()) {
			if (!startEvenIfDisconnected) {
				shutdownInternals();
				operStatus = PlcConnectorOperStatus.STOPPED;
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
					"OPC UA 서버에 연결하지 못했습니다: " + endpointUrl + " — " + lastError);
			}
			scheduleRetry();
		}
	}

	/** 클라이언트를 만들어 연결 — 실패하면 false(뒤에서 다시) */
	private boolean tryConnect() {
		if (!running) {
			return false;
		}
		try {
			OpcUaClient c = OpcUaSessions.create(endpointUrl, settings);
			c.addSessionActivityListener(new SessionActivityListener() {
				@Override
				public void onSessionActive(UaSession session) {
					connected = true;
					generation.incrementAndGet();
					log.info("OPC UA 세션 활성: {} ({})", name, endpointUrl);
					emit(EventType.CONNECT, generation.get() == 1 ? "new" : "retry", endpointUrl);
				}

				@Override
				public void onSessionInactive(UaSession session) {
					connected = false;
					lastError = "세션 끊김";
					lastErrorAt = new Date();
					log.warn("OPC UA 세션 비활성: {} ({})", name, endpointUrl);
					emit(EventType.DISCONNECT, "SESSION_INACTIVE", endpointUrl);
				}
			});
			c.connect();
			client = c;
			connected = true;
			return true;
		} catch (Exception e) {
			lastError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			lastErrorAt = new Date();
			log.warn("OPC UA 연결 실패: {} ({}) — {}", name, endpointUrl, lastError);
			return false;
		}
	}

	private void scheduleRetry() {
		if (!running || retry == null || retry.isShutdown()) {
			return;
		}
		long delay = retryDelayMs;
		retryDelayMs = Math.min(retryDelayMs * 2, 60_000);
		retry.schedule(() -> {
			if (running && client == null && !tryConnect()) {
				scheduleRetry();
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	@Override
	public PlcResult execute(PlcOperation operation) throws ToolControlException {
		if (operStatus == PlcConnectorOperStatus.PAUSED || operStatus == PlcConnectorOperStatus.UNKNOWN
				|| operStatus == PlcConnectorOperStatus.STOPPED) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "요청할 수 없는 운영 상태입니다: " + operStatus);
		}
		OpcUaClient c = client;
		if (c == null || !connected) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "OPC UA 서버와 연결되어 있지 않습니다: " + name
				+ (lastError != null ? " — " + lastError : ""));
		}
		return OpcUaDriver.run(c, operation);
	}

	@Override
	public byte[] exchangeFrame(String reqId, byte[] frame) throws ToolControlException {
		throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "OPC UA 는 프레임을 주고받지 않습니다");
	}

	@Override
	public boolean testConnection() {
		return connected;
	}

	@Override
	public AdapterStatus status() {
		return new AdapterStatus(operStatus, connected ? 1 : 0, lastError, lastErrorAt);
	}

	@Override
	public void pause() {
		operStatus = PlcConnectorOperStatus.PAUSED;
	}

	@Override
	public void resume() {
		operStatus = PlcConnectorOperStatus.RESUMED;
	}

	@Override
	public void stop() {
		operStatus = PlcConnectorOperStatus.STOPPED;
		shutdownInternals();
		emit(EventType.SHUTDOWN, "STOP_REQUEST", endpointUrl);
	}

	@Override
	public void shutdownQuietly() {
		quiet = true;
		operStatus = PlcConnectorOperStatus.STOPPED;
		shutdownInternals();
		log.info("OPC UA 세션을 닫았습니다(앱 종료): {}", name);
	}

	private synchronized void shutdownInternals() {
		running = false;
		connected = false;
		if (retry != null) {
			retry.shutdownNow();
		}
		OpcUaClient c = client;
		client = null;
		if (c != null) {
			try {
				c.disconnectAsync().get(3, TimeUnit.SECONDS);
			} catch (Exception e) {
				log.debug("OPC UA 연결 닫기: {} — {}", name, e.getMessage());
			}
		}
	}

	@Override
	public void destroy() {
		// 붙잡은 것이 없다 — stop·shutdownQuietly 가 닫는다
	}

	private void emit(EventType type, String cause, String description) {
		if (quiet || listener == null) {
			return;
		}
		try {
			PlcConnectorEvent event = new PlcConnectorEvent();
			event.setPlcConnectorId(id);
			event.setPlcConnectorName(name);
			event.setOperStatus(operStatus);
			event.setActiveChannelCount(connected ? 1 : 0);
			event.setType(type);
			event.setCause(cause);
			event.setDescription(description);
			event.setTimestamp(new Date());
			event.setRemoteIp(hostIp);
			listener.onEvent(event);
		} catch (RuntimeException e) {
			log.warn("OPC UA 연결 이벤트를 남기지 못했습니다: {} {}", name, type, e);
		}
	}
}
