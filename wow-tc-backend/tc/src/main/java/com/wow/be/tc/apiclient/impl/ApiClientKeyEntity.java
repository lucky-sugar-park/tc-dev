package com.wow.be.tc.apiclient.impl;

import java.util.Date;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.apiclient.ApiClientKeyInfo;
import com.wow.be.tc.apiclient.ApiClientKeyInfo.KeyStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * API 키 (API_CLIENT_KEY). 키 하나가 행 하나다 — 발급은 INSERT, 폐기는 상태 변경이라 이력이 그대로 남는다.
 * 비밀 부분은 SHA-256 해시로만 둔다.
 */
@Entity
@Table(name = "API_CLIENT_KEY", indexes = @Index(name = "IX_API_CLIENT_KEY_CLIENT", columnList = "CLIENT_ID"))
@Data
@ToString(callSuper = true, exclude = "keyHash")
@EqualsAndHashCode(callSuper = false)
public class ApiClientKeyEntity extends PersistentEntity<ApiClientKeyInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "KEY_ID", length = 40)
	private String keyId;

	@Column(name = "CLIENT_ID", nullable = false, length = 100)
	private String clientId;

	/** 비밀 부분의 SHA-256(16진수 64자) */
	@Column(name = "KEY_HASH", nullable = false, length = 64)
	private String keyHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATUS", nullable = false, length = 20)
	private KeyStatus status;

	@Column(name = "EXPIRES_AT", nullable = false)
	private Date expiresAt;

	@Column(name = "REVOKED_AT")
	private Date revokedAt;

	@Column(name = "LAST_USED_AT")
	private Date lastUsedAt;

	@Column(name = "LAST_USED_IP", length = 64)
	private String lastUsedIp;

	public boolean isExpired(long now) {
		return expiresAt != null && expiresAt.getTime() <= now;
	}

	/** 폐기되지 않았고 만료되지 않음 */
	public boolean isActive(long now) {
		return status == KeyStatus.ACTIVE && !isExpired(now);
	}

	@Override
	public ApiClientKeyInfo info() {
		ApiClientKeyInfo info = new ApiClientKeyInfo();
		info.setKeyId(keyId);
		info.setClientId(clientId);
		info.setStatus(status);
		info.setIssuedAt(createdDate);
		info.setExpiresAt(expiresAt);
		info.setRevokedAt(revokedAt);
		info.setLastUsedAt(lastUsedAt);
		info.setLastUsedIp(lastUsedIp);
		info.setExpired(isExpired(System.currentTimeMillis()));
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	/** 키는 발급·폐기·사용 기록으로만 바뀐다 — 화면 값으로 덮어쓰지 않는다. */
	@Override
	public void from(ApiClientKeyInfo info) {
		throw new UnsupportedOperationException("API 키는 정보 객체로 바꾸지 않습니다");
	}

	@Override
	public String toPlainText() {
		return keyId;
	}
}
