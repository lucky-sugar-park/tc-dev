package com.wow.be.tc.apiclient;

import java.util.Date;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * API 키 하나 (API_CLIENT_KEY). 키 값과 해시는 담지 않는다 — 키 값은 발급 응답({@link IssuedApiKey})에서 한 번만 보인다.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class ApiClientKeyInfo extends AbstractInfo {

	/** 키의 공개 부분. 키 문자열 {@code tck_<keyId>_<secret>} 의 가운데 */
	private String keyId;

	private String clientId;

	private KeyStatus status;

	/** 발급 시각 */
	private Date issuedAt;

	private Date expiresAt;

	private Date revokedAt;

	private Date lastUsedAt;

	private String lastUsedIp;

	// ── 조회 전용 ──

	/** 만료됨(상태는 ACTIVE 그대로, 검증할 때 거부) */
	private boolean expired;

	/** 활성 키인데 90일 넘게 쓰이지 않음 — 폐기 후보 */
	private boolean unused;

	public enum KeyStatus {
		ACTIVE, REVOKED
	}

	@Override
	public String toPlainText() {
		return keyId + " [" + clientId + ", " + status + (expired ? ", 만료" : "") + "]";
	}
}
