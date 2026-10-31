package com.wow.be.tc.plc.protocol.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.Map;
import java.util.Optional;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;

/**
 * OPC UA 세션 만들기 — 어댑터·연결 시험·찾아보기가 같은 설정으로 (docs/opcua-design.md §3 O1·O2).
 * D1 은 SecurityPolicy None + 익명만 — 서버가 None 엔드포인트를 주지 않으면 연결 실패.
 */
public final class OpcUaSessions {

	public static final String APPLICATION_URI = "urn:wow:tc:opcua-client";

	private OpcUaSessions() {
	}

	/** {@code opc.tcp://host:port/path} — 포트가 없으면 4840 */
	public static String endpointUrl(String host, Integer port, Map<String, Object> settings) {
		Object path = settings != null ? settings.get("endpointPath") : null;
		String p = path != null ? path.toString().trim() : "";
		if (!p.isEmpty() && !p.startsWith("/")) {
			p = "/" + p;
		}
		return "opc.tcp://" + host + ":" + (port != null && port > 0 ? port : OpcUaDriver.DEFAULT_PORT) + p;
	}

	/** 클라이언트를 만든다(아직 연결하지 않음). 설정은 정규화한 커넥터 설정 */
	public static OpcUaClient create(String endpointUrl, Map<String, Object> settings) throws UaException {
		long requestTimeout = longOf(settings, "requestTimeoutMs", OpcUaDriver.DEFAULT_REQUEST_TIMEOUT_MS);
		long sessionTimeout = longOf(settings, "sessionTimeoutMs", OpcUaDriver.DEFAULT_SESSION_TIMEOUT_MS);
		return OpcUaClient.create(endpointUrl,
			endpoints -> endpoints.stream()
				.filter(e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri()) && e.getSecurityMode() == MessageSecurityMode.None)
				.findFirst()
				.map(e -> Optional.of(e))
				.orElse(Optional.empty()),
			transport -> transport.setConnectTimeout(uint(Math.min(requestTimeout, 10_000))),
			client -> client
				.setApplicationName(LocalizedText.english("WOW-TC"))
				.setApplicationUri(APPLICATION_URI)
				.setRequestTimeout(uint(requestTimeout))
				.setSessionTimeout(uint(sessionTimeout)));
	}

	static long longOf(Map<String, Object> settings, String key, long def) {
		Object v = settings != null ? settings.get(key) : null;
		return v instanceof Number n ? n.longValue() : def;
	}
}
