package com.wow.be.tc.plc.protocol.opcua;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.security.SecretCipher;

import lombok.extern.slf4j.Slf4j;

/**
 * OPC UA 인증서 보관(docs/opcua-design.md O15·O16) — tc 클라이언트 인증서 하나와 커넥터별로 믿은 서버 인증서.
 *
 * <ul>
 *   <li>클라이언트 인증서: 처음 필요할 때 만들어 {@code <pki>/client.pfx}(PKCS#12)에 둔다. 키 저장소 비밀번호는 {@code TC_SECRET_KEY} 에서
 *       끌어낸다(HMAC) — 같은 키를 쓰는 다른 인스턴스도 같은 파일을 연다. 서버 관리자에게 줄 공개 인증서는 {@code client.der}.</li>
 *   <li>서버 인증서(TOFU): 커넥터별 {@code <pki>/trusted/<커넥터>.der}. 처음 본 인증서를 남기고, 다음부터 다르면 거절.</li>
 * </ul>
 * 어댑터는 리플렉션으로 만들어져 빈을 받지 못하므로 {@link #current()} 로 꺼낸다.
 */
@Slf4j
@Component
public class OpcUaPki {

	private static volatile OpcUaPki current;

	private final Path dir;

	private final SecretCipher cipher;

	private final byte[] secretKey;

	private volatile KeyPair keyPair;

	private volatile X509Certificate certificate;

	public OpcUaPki(@Value("${tc.opcua.pki-dir:pki/opcua}") String dir, SecretCipher cipher, @Value("${tc.secret-key:}") String secretKey) {
		this.dir = Path.of(dir).toAbsolutePath();
		this.cipher = cipher;
		this.secretKey = secretKey != null && !secretKey.isBlank() ? secretKey.trim().getBytes(StandardCharsets.UTF_8) : null;
		current = this;
	}

	/** 스프링 밖(어댑터)에서 — 앱이 뜨기 전이면 null */
	public static OpcUaPki current() {
		return current;
	}

	public Path dir() {
		return dir;
	}

	/** 저장한 비밀번호(암호문) → 평문 */
	public String decrypt(String passwordEnc) throws ToolControlException {
		return cipher.decrypt(passwordEnc);
	}

	// ── 클라이언트 인증서 ──

	public record ClientIdentity(KeyPair keyPair, X509Certificate certificate) {
	}

	/** tc 클라이언트 인증서 — 없으면 만든다. {@code TC_SECRET_KEY} 가 없으면 503 */
	public synchronized ClientIdentity client() throws ToolControlException {
		if (keyPair != null) {
			return new ClientIdentity(keyPair, certificate);
		}
		if (secretKey == null) {
			throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE,
				"OPC UA 보안 정책에는 tc 클라이언트 인증서가 필요하고, 인증서 보관에 TC_SECRET_KEY 가 필요합니다");
		}
		Path pfx = dir.resolve("client.pfx");
		char[] password = keystorePassword();
		try {
			KeyStore ks = KeyStore.getInstance("PKCS12");
			if (Files.exists(pfx)) {
				try (InputStream in = Files.newInputStream(pfx)) {
					ks.load(in, password);
				}
				PrivateKey key = (PrivateKey) ks.getKey("tc", password);
				X509Certificate cert = (X509Certificate) ks.getCertificate("tc");
				keyPair = new KeyPair(cert.getPublicKey(), key);
				certificate = cert;
				log.info("OPC UA 클라이언트 인증서를 읽었습니다: {}", pfx);
			} else {
				KeyPair kp = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
				SelfSignedCertificateBuilder b = new SelfSignedCertificateBuilder(kp)
					.setCommonName("WOW-TC OPC UA client").setOrganization("wow").setApplicationUri(OpcUaSessions.APPLICATION_URI)
					.setValidityPeriod(java.time.Period.ofYears(10)).addDnsName("localhost");
				try {
					InetAddress local = InetAddress.getLocalHost();
					b.addDnsName(local.getHostName()).addIpAddress(local.getHostAddress());
				} catch (IOException e) {
					// 이름을 모르면 localhost 만
				}
				X509Certificate cert = b.build();
				ks.load(null, password);
				ks.setKeyEntry("tc", kp.getPrivate(), password, new X509Certificate[] { cert });
				Files.createDirectories(dir);
				try (OutputStream out = Files.newOutputStream(pfx)) {
					ks.store(out, password);
				}
				Files.write(dir.resolve("client.der"), cert.getEncoded());
				keyPair = kp;
				certificate = cert;
				log.info("OPC UA 클라이언트 인증서를 만들었습니다: {} (서버 관리자에게 client.der 를 믿게 하세요)", pfx);
			}
			return new ClientIdentity(keyPair, certificate);
		} catch (Exception e) {
			throw ToolControlException.of(ToolControlException.ERR_TC, "OPC UA 클라이언트 인증서를 만들거나 읽지 못했습니다: " + e.getMessage()
				+ " (TC_SECRET_KEY 가 바뀌었으면 " + pfx + " 를 지우고 서버에 새 인증서를 다시 믿게 하세요)");
		}
	}

	/** 서버 관리자에게 줄 공개 인증서(DER) */
	public byte[] clientCertificateDer() throws ToolControlException {
		try {
			return client().certificate().getEncoded();
		} catch (java.security.cert.CertificateEncodingException e) {
			throw ToolControlException.of(ToolControlException.ERR_TC, "인증서를 내보내지 못했습니다");
		}
	}

	private char[] keystorePassword() {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal("tc-opcua-keystore".getBytes(StandardCharsets.UTF_8))).toCharArray();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// ── 서버 인증서(TOFU) ──

	/** 커넥터 이름 → 파일 이름(경로 문자 막기) */
	private Path trustedFile(String connector) {
		return dir.resolve("trusted").resolve(connector.replaceAll("[^A-Za-z0-9_.-]", "_") + ".der");
	}

	/**
	 * 서버 인증서 검사기 — {@code TRUST_ALL} 이면 모두 믿고, 아니면 처음 본 인증서를 커넥터별로 남겨 그 뒤로 같은 것만 믿는다.
	 */
	public CertificateValidator serverValidator(String connector, String trust) {
		if (OpcUaDriver.TRUST_ALL.equals(trust)) {
			return new CertificateValidator.InsecureCertificateValidator();
		}
		return (List<X509Certificate> chain, String applicationUri, String[] validHostNames) -> {
			if (chain == null || chain.isEmpty()) {
				throw new UaException(StatusCodes.Bad_SecurityChecksFailed, "서버 인증서가 없습니다");
			}
			X509Certificate server = chain.get(0);
			Path file = trustedFile(connector);
			try {
				if (Files.exists(file)) {
					X509Certificate known = (X509Certificate) CertificateFactory.getInstance("X.509")
						.generateCertificate(new java.io.ByteArrayInputStream(Files.readAllBytes(file)));
					if (!java.util.Arrays.equals(known.getEncoded(), server.getEncoded())) {
						throw new UaException(StatusCodes.Bad_CertificateUntrusted, "서버 인증서가 처음 믿은 것과 다릅니다(지문 " + thumbprint(server)
							+ ", 믿은 것 " + thumbprint(known) + ") — 서버 인증서를 바꾼 것이 맞으면 커넥터에서 \"서버 인증서 잊기\"");
					}
					return;
				}
				Files.createDirectories(file.getParent());
				Files.write(file, server.getEncoded());
				log.info("OPC UA 서버 인증서를 처음 믿었습니다: {} (지문 {})", connector, thumbprint(server));
			} catch (UaException e) {
				throw e;
			} catch (Exception e) {
				throw new UaException(StatusCodes.Bad_SecurityChecksFailed, "서버 인증서 검사 실패: " + e.getMessage());
			}
		};
	}

	/** 믿은 서버 인증서를 지운다 — 다음 연결 때 다시 처음 믿는다. 지웠으면 true */
	public boolean forgetServer(String connector) throws IOException {
		return Files.deleteIfExists(trustedFile(connector));
	}

	/** 믿은 서버 인증서 지문(SHA-1, 화면용) — 없으면 null */
	public String trustedThumbprint(String connector) {
		Path file = trustedFile(connector);
		try {
			return Files.exists(file) ? thumbprint((X509Certificate) CertificateFactory.getInstance("X.509")
				.generateCertificate(new java.io.ByteArrayInputStream(Files.readAllBytes(file)))) : null;
		} catch (Exception e) {
			return null;
		}
	}

	static String thumbprint(X509Certificate cert) {
		try {
			return HexFormat.ofDelimiter(":").withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(cert.getEncoded()));
		} catch (Exception e) {
			return "?";
		}
	}
}
