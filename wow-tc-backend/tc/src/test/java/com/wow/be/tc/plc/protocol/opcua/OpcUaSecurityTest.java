package com.wow.be.tc.plc.protocol.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.security.SecretCipher;

/** OPC UA D2 — 보안 정책·사용자 이름·서버 인증서 TOFU·nsu= 주소 (docs/opcua-design.md §7) */
class OpcUaSecurityTest {

	static TestOpcUaServer server;

	static TestOpcUaServer other;

	@TempDir
	static Path pkiDir;

	static OpcUaPki pki;

	final OpcUaDriver driver = new OpcUaDriver();

	@BeforeAll
	static void start() throws Exception {
		server = new TestOpcUaServer();
		other = new TestOpcUaServer();
		String key = Base64.getEncoder().encodeToString(new byte[32]);
		pki = new OpcUaPki(pkiDir.toString(), new SecretCipher(key), key);
	}

	@AfterAll
	static void stop() throws Exception {
		server.close();
		other.close();
	}

	private Map<String, Object> settings(Map<String, Object> extra) {
		Map<String, Object> in = new HashMap<>(Map.of("endpointPath", "/test"));
		in.putAll(extra);
		return driver.normalizeSettings(in, null);
	}

	private static String url(TestOpcUaServer s) {
		return "opc.tcp://127.0.0.1:" + s.port + "/test";
	}

	private static PointInfo point(String address) {
		PointInfo p = new PointInfo();
		p.setName("temp");
		p.setAddress(address);
		p.setType(PointType.FLOAT64);
		return p;
	}

	@Test
	void 설정_규칙() {
		assertThat(settings(Map.of("securityPolicy", "basic256sha256"))).containsEntry("securityMode", "SIGN_AND_ENCRYPT");
		assertThat(settings(Map.of("securityPolicy", "NONE", "securityMode", "SIGN"))).containsEntry("securityMode", "NONE");
		assertThatThrownBy(() -> settings(Map.of("authMode", "USERNAME"))).hasMessageContaining("username");
		assertThatThrownBy(() -> settings(Map.of("securityPolicy", "BASIC128"))).hasMessageContaining("securityPolicy");
		assertThat(settings(Map.of())).containsEntry("serverTrust", "TRUST_ON_FIRST_USE").containsEntry("authMode", "ANONYMOUS");
		driver.validatePoint(point("nsu=urn:x;s=A.B"), null);
		assertThatThrownBy(() -> driver.validatePoint(point("nsu=;;"), null)).hasMessageContaining("NodeId");
	}

	@Test
	void 암호화_로그인_nsu_주소() throws Exception {
		Map<String, Object> s = settings(Map.of("securityPolicy", "BASIC256SHA256", "authMode", "USERNAME", "username", TestOpcUaServer.USER));
		OpcUaClient c = OpcUaSessions.create(url(server), s, "secure-1", TestOpcUaServer.PASSWORD);
		c.connect();
		try {
			PlcResult r = OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, s, PlcOperation.Kind.READ,
				List.of(point(server.expanded("Temp"))), Map.of(), "r"));
			assertThat(r.status().ok()).isTrue();
			assertThat(r.values()).containsKey("temp");
			// 서버에 없는 네임스페이스 → 설정 오류(409)
			assertThatThrownBy(() -> OpcUaDriver.run(c, new PlcOperation(OpcUaDriver.PROTOCOL, s, PlcOperation.Kind.READ,
				List.of(point("nsu=urn:nope;s=X")), Map.of(), "r2")))
				.isInstanceOf(ToolControlException.class).hasMessageContaining("네임스페이스");
		} finally {
			c.disconnect();
		}
		assertThat(Files.exists(pkiDir.resolve("client.pfx"))).isTrue();
		assertThat(Files.exists(pkiDir.resolve("client.der"))).isTrue();
		assertThat(pki.trustedThumbprint("secure-1")).isNotNull();
		// 다시 읽어도 같은 인증서(키 저장소를 연다)
		assertThat(new OpcUaPki(pkiDir.toString(), new SecretCipher(Base64.getEncoder().encodeToString(new byte[32])),
			Base64.getEncoder().encodeToString(new byte[32])).clientCertificateDer()).isEqualTo(pki.clientCertificateDer());
	}

	@Test
	void 틀린_비밀번호와_없는_엔드포인트() throws Exception {
		Map<String, Object> s = settings(Map.of("authMode", "USERNAME", "username", TestOpcUaServer.USER));
		OpcUaClient wrong = OpcUaSessions.create(url(server), s, "pw-1", "nope");
		assertThatThrownBy(wrong::connect).hasMessageMatching("(?s).*(Bad_UserAccessDenied|Bad_IdentityTokenInvalid).*");
		assertThatThrownBy(() -> OpcUaSessions.create(url(server), s, "pw-1", null)).hasMessageContaining("비밀번호");
		Map<String, Object> pss = settings(Map.of("securityPolicy", "AES256_SHA256_RSAPSS"));
		assertThatThrownBy(() -> OpcUaSessions.create(url(server), pss, "pss-1", null)).hasMessageContaining("엔드포인트가 없습니다")
			.hasMessageContaining("Basic256Sha256");
	}

	@Test
	void 서버_인증서_TOFU() throws Exception {
		Map<String, Object> s = settings(Map.of("securityPolicy", "BASIC256SHA256"));
		OpcUaClient first = OpcUaSessions.create(url(server), s, "tofu-1", null);
		first.connect();
		first.disconnect();
		String pinned = pki.trustedThumbprint("tofu-1");
		assertThat(pinned).isNotNull();
		// 같은 커넥터 이름으로 인증서가 다른 서버 → 거절
		OpcUaClient changed = OpcUaSessions.create(url(other), s, "tofu-1", null);
		assertThatThrownBy(changed::connect).isInstanceOf(Exception.class);
		assertThat(pki.trustedThumbprint("tofu-1")).isEqualTo(pinned);
		// 잊으면 다시 처음 믿는다
		assertThat(pki.forgetServer("tofu-1")).isTrue();
		OpcUaClient again = OpcUaSessions.create(url(other), s, "tofu-1", null);
		again.connect();
		again.disconnect();
		assertThat(pki.trustedThumbprint("tofu-1")).isNotEqualTo(pinned);
		// TRUST_ALL 은 남기지 않고 붙는다
		Map<String, Object> all = settings(Map.of("securityPolicy", "BASIC256SHA256", "serverTrust", "TRUST_ALL"));
		OpcUaClient trustAll = OpcUaSessions.create(url(server), all, "all-1", null);
		trustAll.connect();
		trustAll.disconnect();
		assertThat(pki.trustedThumbprint("all-1")).isNull();
	}
}
