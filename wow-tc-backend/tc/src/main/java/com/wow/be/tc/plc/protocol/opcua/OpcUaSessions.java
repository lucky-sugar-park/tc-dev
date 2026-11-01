package com.wow.be.tc.plc.protocol.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.util.EndpointUtil;

import com.wow.be.tc.ToolControlException;

/**
 * OPC UA 세션 만들기 — 어댑터·연결 시험·찾아보기가 같은 설정으로 (docs/opcua-design.md O1·O2·O13~O16).
 *
 * <ul>
 *   <li>엔드포인트: 서버가 주는 목록에서 설정 {@code securityPolicy}·{@code securityMode} 가 같은 것. 없으면 서버가 주는 조합을 오류에 보인다.
 *       주소의 호스트·포트는 커넥터 값으로 바꾼다(서버가 자기 이름을 다르게 알려 줘도 붙게).</li>
 *   <li>보안 정책이 있으면 tc 클라이언트 인증서({@link OpcUaPki}). 서버 인증서는 커넥터 설정 {@code serverTrust} 로 검사(TOFU·모두 믿기).</li>
 *   <li>로그인: {@code authMode=USERNAME} 이면 사용자 이름·비밀번호(복호화한 평문을 받는다), 아니면 익명.</li>
 * </ul>
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

	/** 보안 없이·익명 — 시험용 */
	public static OpcUaClient create(String endpointUrl, Map<String, Object> settings) throws UaException, ToolControlException {
		return create(endpointUrl, settings, null, null);
	}

	/**
	 * 클라이언트를 만든다(아직 연결하지 않음).
	 *
	 * @param connector 커넥터 이름 — 서버 인증서를 믿은 기록의 열쇠(없으면 보안·로그인을 쓸 수 없다)
	 * @param password  로그인 비밀번호 평문({@code authMode=USERNAME} 일 때)
	 */
	public static OpcUaClient create(String endpointUrl, Map<String, Object> settings, String connector, String password)
			throws UaException, ToolControlException {
		long requestTimeout = longOf(settings, "requestTimeoutMs", OpcUaDriver.DEFAULT_REQUEST_TIMEOUT_MS);
		long sessionTimeout = longOf(settings, "sessionTimeoutMs", OpcUaDriver.DEFAULT_SESSION_TIMEOUT_MS);
		SecurityPolicy policy = policy(text(settings, "securityPolicy", OpcUaDriver.POLICY_NONE));
		MessageSecurityMode mode = mode(text(settings, "securityMode", "NONE"));
		boolean username = OpcUaDriver.AUTH_USERNAME.equals(text(settings, "authMode", OpcUaDriver.AUTH_ANONYMOUS));
		boolean secure = policy != SecurityPolicy.None;

		OpcUaPki pki = OpcUaPki.current();
		CertificateValidator validator = null;
		OpcUaPki.ClientIdentity identity = null;
		if (secure || username) {
			if (pki == null || connector == null) {
				throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "OPC UA 보안·로그인에 필요한 인증서 보관소가 없습니다");
			}
			validator = pki.serverValidator(connector, text(settings, "serverTrust", OpcUaDriver.TRUST_ON_FIRST_USE));
		}
		if (secure) {
			identity = pki.client();
		}
		if (username && (password == null || password.isEmpty())) {
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED,
				"OPC UA 로그인(authMode=USERNAME)에 비밀번호가 없습니다 — 커넥터에 비밀번호를 넣으세요: " + connector);
		}

		String host = EndpointUtil.getHost(endpointUrl);
		int port = EndpointUtil.getPort(endpointUrl);
		AtomicReference<List<String>> offered = new AtomicReference<>(List.of());
		CertificateValidator serverValidator = validator;
		OpcUaPki.ClientIdentity clientIdentity = identity;
		try {
			return OpcUaClient.create(endpointUrl,
				endpoints -> {
					List<String> seen = new ArrayList<>();
					for (EndpointDescription e : endpoints) {
						seen.add(SecurityPolicy.fromUriSafe(e.getSecurityPolicyUri()).map(Enum::name).orElse(e.getSecurityPolicyUri())
							+ "/" + e.getSecurityMode());
					}
					offered.set(seen);
					return endpoints.stream()
						.filter(e -> policy.getUri().equals(e.getSecurityPolicyUri()) && e.getSecurityMode() == mode)
						.findFirst()
						.map(e -> Optional.of(EndpointUtil.updateUrl(e, host, port)))
						.orElse(Optional.empty());
				},
				transport -> transport.setConnectTimeout(uint(Math.min(requestTimeout, 10_000))),
				client -> {
					client.setApplicationName(LocalizedText.english("WOW-TC"))
						.setApplicationUri(APPLICATION_URI)
						.setRequestTimeout(uint(requestTimeout))
						.setSessionTimeout(uint(sessionTimeout));
					if (clientIdentity != null) {
						client.setKeyPair(clientIdentity.keyPair()).setCertificate(clientIdentity.certificate())
							.setCertificateChain(new X509Certificate[] { clientIdentity.certificate() });
					}
					if (serverValidator != null) {
						client.setCertificateValidator(serverValidator);
					}
					client.setIdentityProvider(username
						? new UsernameProvider(text(settings, "username", ""), password,
							serverValidator != null ? serverValidator : new CertificateValidator.InsecureCertificateValidator())
						: new AnonymousProvider());
				});
		} catch (UaException e) {
			if (!offered.get().isEmpty() && offered.get().stream().noneMatch(o -> o.equals(policy.name() + "/" + mode))) {
				throw new UaException(StatusCodes.Bad_SecurityPolicyRejected, "서버에 " + policy.name() + "/" + mode
					+ " 엔드포인트가 없습니다 — 서버가 주는 것: " + String.join(", ", offered.get()));
			}
			throw e;
		}
	}

	static SecurityPolicy policy(String name) {
		return switch (name) {
			case "BASIC256SHA256" -> SecurityPolicy.Basic256Sha256;
			case "AES128_SHA256_RSAOAEP" -> SecurityPolicy.Aes128_Sha256_RsaOaep;
			case "AES256_SHA256_RSAPSS" -> SecurityPolicy.Aes256_Sha256_RsaPss;
			default -> SecurityPolicy.None;
		};
	}

	static MessageSecurityMode mode(String name) {
		return switch (name) {
			case "SIGN" -> MessageSecurityMode.Sign;
			case "SIGN_AND_ENCRYPT" -> MessageSecurityMode.SignAndEncrypt;
			default -> MessageSecurityMode.None;
		};
	}

	static String text(Map<String, Object> settings, String key, String def) {
		Object v = settings != null ? settings.get(key) : null;
		return v != null && !v.toString().isBlank() ? v.toString().trim() : def;
	}

	static long longOf(Map<String, Object> settings, String key, long def) {
		Object v = settings != null ? settings.get(key) : null;
		return v instanceof Number n ? n.longValue() : def;
	}
}
