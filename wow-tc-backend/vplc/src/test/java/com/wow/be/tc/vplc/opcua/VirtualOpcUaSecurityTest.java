package com.wow.be.tc.vplc.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExpandedNodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.Security;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;

/** 가상 OPC UA 서버 보안 — Basic256Sha256 SignAndEncrypt·사용자 이름, 익명 거절(require-login), 두 번째 네임스페이스로 번호 3 */
class VirtualOpcUaSecurityTest {

	static VirtualOpcUaServer server;

	static KeyPair keyPair;

	static X509Certificate certificate;

	@BeforeAll
	static void start() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		server = new VirtualOpcUaServer("127.0.0.1", "127.0.0.1", port, "/vplc",
			List.of(new TagSpec("Line1.Temp", TagType.FLOAT64, 25.5, null, null)),
			new Security(Map.of("tc-e2e", "pw-123"), true, true));
		keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
		certificate = new SelfSignedCertificateBuilder(keyPair).setCommonName("test client").setApplicationUri("urn:test:client")
			.addDnsName("localhost").build();
	}

	@AfterAll
	static void stop() throws Exception {
		server.close();
	}

	private static OpcUaClient client(SecurityPolicy policy, MessageSecurityMode mode, IdentityProvider identity) throws Exception {
		return OpcUaClient.create(server.endpointUrl(),
			endpoints -> endpoints.stream()
				.filter(e -> policy.getUri().equals(e.getSecurityPolicyUri()) && e.getSecurityMode() == mode).findFirst()
				.map(Optional::of).orElse(Optional.empty()),
			transport -> { },
			c -> c.setApplicationName(LocalizedText.english("test")).setApplicationUri("urn:test:client")
				.setKeyPair(keyPair).setCertificate(certificate).setCertificateChain(new X509Certificate[] { certificate })
				.setCertificateValidator(new CertificateValidator.InsecureCertificateValidator())
				.setIdentityProvider(identity).setRequestTimeout(uint(5000)));
	}

	@Test
	void 암호화_엔드포인트와_사용자_이름() throws Exception {
		assertThat(server.namespaceIndex()).isEqualTo(3);
		OpcUaClient c = client(SecurityPolicy.Basic256Sha256, MessageSecurityMode.SignAndEncrypt, new UsernameProvider("tc-e2e", "pw-123"));
		c.connect();
		try {
			// nsu= 주소를 세션의 네임스페이스 표로 번호로 바꿔 읽는다
			NodeId temp = ExpandedNodeId.parse(server.expandedNodeId("Line1.Temp")).toNodeId(c.getNamespaceTable()).orElseThrow();
			assertThat(temp.getNamespaceIndex().intValue()).isEqualTo(3);
			assertThat(c.readValue(0, TimestampsToReturn.Neither, temp).getValue().getValue()).isEqualTo(25.5);
		} finally {
			c.disconnect();
		}
	}

	@Test
	void 틀린_비밀번호와_익명은_거절() throws Exception {
		OpcUaClient wrong = client(SecurityPolicy.None, MessageSecurityMode.None, new UsernameProvider("tc-e2e", "nope"));
		assertThatThrownBy(wrong::connect).hasMessageMatching("(?s).*(Bad_UserAccessDenied|Bad_IdentityTokenInvalid).*");
		OpcUaClient anon = client(SecurityPolicy.Basic256Sha256, MessageSecurityMode.Sign, new AnonymousProvider());
		assertThatThrownBy(anon::connect).isInstanceOf(Exception.class);
	}
}
