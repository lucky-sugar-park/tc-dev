package com.wow.be.tc.vplc.opcua;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.milo.opcua.sdk.server.Session;
import org.eclipse.milo.opcua.sdk.server.SessionListener;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.ConnectionEvents;
import com.wow.be.tc.vplc.machine.VirtualDevice;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.Security;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagView;

import lombok.extern.slf4j.Slf4j;

/**
 * 가상 OPC UA 서버 하나(docs/virtual-devices.md) — 엔드포인트 {@code opc.tcp://<IP>:<포트><path>}, 태그는 VPLC_OPCUA_TAG.
 * 시작하면 Milo 서버를 띄우고 정지하면 내린다(태그 지금 값은 그때 처음 값으로). 일시정지는 값 읽기·쓰기에 답하지 않기.
 * 세션이 열리고 닫히면 연결 이력에 남긴다(클라이언트 주소, 세션 아이디).
 */
@Slf4j
public final class OpcUaVirtualDevice implements VirtualDevice {

	public static final String DEFAULT_PATH = "/vplc";

	private final String id;

	private final String name;

	private final String ipAddress;

	private final int port;

	private final String path;

	private final Security security;

	/** 태그 정의(경로 → 정의) — 실행 중에는 서버에도 있다 */
	private final Map<String, TagSpec> specs = new LinkedHashMap<>();

	/** 연결 이력에 남긴 세션(세션 아이디 → 클라이언트 주소) */
	private final Map<String, String> sessions = new ConcurrentHashMap<>();

	private volatile VirtualOpcUaServer server;

	private volatile PlcStatus status = PlcStatus.PUBLISHED;

	public OpcUaVirtualDevice(VirtualPlcInfo info, List<TagSpec> tags, Map<String, String> users) {
		this.id = info.getId();
		this.name = info.getName();
		this.ipAddress = info.getIpAddress() != null && !info.getIpAddress().isBlank() ? info.getIpAddress() : "127.0.0.1";
		this.port = info.getStartPort();
		Map<String, Object> s = info.getSettings() != null ? info.getSettings() : Map.of();
		String p = s.get("path") != null ? String.valueOf(s.get("path")) : DEFAULT_PATH;
		this.path = p.isBlank() ? "" : (p.startsWith("/") ? p : "/" + p);
		this.security = new Security(users, bool(s.get("requireLogin"), false), bool(s.get("trustAllClients"), true));
		tags.forEach(t -> specs.put(t.path(), t));
	}

	private static boolean bool(Object v, boolean def) {
		return v == null ? def : v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
	}

	public String endpointUrl() {
		return "opc.tcp://" + ipAddress + ":" + port + path;
	}

	/** 실행 중이면 서버의 네임스페이스 번호, 아니면 -1 */
	public int namespaceIndex() {
		VirtualOpcUaServer s = server;
		return s != null ? s.namespaceIndex() : -1;
	}

	public int sessionCount() {
		VirtualOpcUaServer s = server;
		return s != null ? s.sessionCount() : 0;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Protocol getProtocol() {
		return Protocol.OPC_UA;
	}

	@Override
	public PlcStatus getStatus() {
		return status;
	}

	@Override
	public void setStatus(PlcStatus status) {
		this.status = status;
	}

	@Override
	public synchronized void start() throws VirtualPlcException {
		if (server == null) {
			try {
				VirtualOpcUaServer s = new VirtualOpcUaServer(ipAddress, ipAddress, port, path, List.copyOf(specs.values()), security);
				s.addSessionListener(new SessionListener() {
					@Override
					public void onSessionCreated(Session session) {
						// 세션을 만들 때는 아직 활성화 전 — 주소는 그때도 있다
						String ip = session.getClientAddress() != null ? session.getClientAddress().getHostAddress() : "unknown";
						sessions.put(session.getSessionId().toParseableString(), ip);
						ConnectionEvents.dispatch(id, port, ip, session.getSessionId().toParseableString(), true,
							"OPC UA session created: " + session.getSessionName());
					}

					@Override
					public void onSessionClosed(Session session) {
						closed(session.getSessionId().toParseableString());
					}
				});
				server = s;
			} catch (Exception e) {
				throw new VirtualPlcException(VirtualPlcException.ERR_409, "OPC UA 서버를 띄울 수 없습니다: " + endpointUrl() + " — " + e.getMessage(), e);
			}
		}
		server.pause(false);
		status = PlcStatus.RUNNING;
	}

	private void closed(String sessionId) {
		String ip = sessions.remove(sessionId);
		if (ip != null) {
			ConnectionEvents.dispatch(id, port, ip, sessionId, false, "OPC UA session closed");
		}
	}

	@Override
	public synchronized void stop() {
		VirtualOpcUaServer s = server;
		server = null;
		if (s != null) {
			try {
				s.close();
			} catch (Exception e) {
				log.warn("OPC UA 서버를 내리지 못했습니다: {} — {}", endpointUrl(), e.toString());
			}
		}
		List.copyOf(sessions.keySet()).forEach(this::closed);
		status = PlcStatus.STOPPED;
	}

	@Override
	public void pause() throws VirtualPlcException {
		running("일시정지").pause(true);
		status = PlcStatus.PAUSED;
	}

	@Override
	public void resume() throws VirtualPlcException {
		running("재개").pause(false);
		status = PlcStatus.RUNNING;
	}

	@Override
	public void release() {
		stop();
	}

	@Override
	public int dropConnections() {
		VirtualOpcUaServer s = server;
		return s != null ? s.killSessions() : 0;
	}

	private VirtualOpcUaServer running(String what) throws VirtualPlcException {
		VirtualOpcUaServer s = server;
		if (s == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "실행 중이 아니라 " + what + "할 수 없습니다: " + name + " (" + status + ")");
		}
		return s;
	}

	/** 태그 목록 — 실행 중이면 지금 값, 아니면 처음 값(NodeId 는 nsu= 만) */
	public synchronized List<TagView> tags() {
		VirtualOpcUaServer s = server;
		if (s != null) {
			return s.list();
		}
		List<TagView> out = new ArrayList<>();
		specs.values().stream().sorted(java.util.Comparator.comparing(TagSpec::path)).forEach(t -> out.add(new TagView(t.path(), null,
			"nsu=" + VirtualOpcUaServer.NAMESPACE_URI + ";s=" + t.path(), t.type(), t.value(), t.simulation(), t.description())));
		return out;
	}

	/** 태그를 더한다(실행 중이면 서버에도) — 정의 검증은 서버가 한다 */
	public synchronized void addTag(TagSpec spec) throws VirtualPlcException {
		if (specs.containsKey(spec.path())) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "이미 있는 태그입니다: " + spec.path());
		}
		VirtualOpcUaServer s = server;
		if (s != null) {
			s.add(spec);
		} else {
			VirtualOpcUaServer.validate(spec);
		}
		specs.put(spec.path(), spec);
	}

	public synchronized void removeTag(String tagPath) throws VirtualPlcException {
		if (specs.remove(tagPath) == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_404, "없는 태그입니다: " + tagPath);
		}
		VirtualOpcUaServer s = server;
		if (s != null) {
			s.remove(tagPath);
		}
	}

	/** 지금 값을 바꾼다 — 실행 중일 때만(값은 메모리) */
	public TagView setValue(String tagPath, Object value) throws VirtualPlcException {
		if (!specs.containsKey(tagPath)) {
			throw new VirtualPlcException(VirtualPlcException.ERR_404, "없는 태그입니다: " + tagPath);
		}
		return running("값을 바꿀").set(tagPath, value);
	}
}
