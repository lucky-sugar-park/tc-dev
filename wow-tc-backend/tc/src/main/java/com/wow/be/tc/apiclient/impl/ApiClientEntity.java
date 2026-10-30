package com.wow.be.tc.apiclient.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.apiclient.ApiClientInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * API 클라이언트 (API_CLIENT). 목록 값(허용 opcode·IP·pushUrl)은 쉼표로 이어 한 칼럼에 둔다.
 *
 * <p>삭제는 행을 지우지 않고 {@code DELETED_AT} 을 남긴다 — 삭제한 clientId 를 다시 만들면 감사 기록(PRINCIPAL)과
 * 섞이므로 재사용을 막기 위해서다(설계 §6.6).
 */
@Entity
@Table(name = "API_CLIENT")
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class ApiClientEntity extends PersistentEntity<ApiClientInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "CLIENT_ID", length = 100)
	private String clientId;

	@Column(name = "NAME", nullable = false, length = 200)
	private String name;

	@Column(name = "DESCRIPTION", length = 1024)
	private String description;

	@Column(name = "ENABLED", nullable = false)
	private boolean enabled;

	@Column(name = "ALLOWED_OPCODES", length = 4000)
	private String allowedOpcodes;

	@Column(name = "ALLOWED_IPS", length = 2000)
	private String allowedIps;

	@Column(name = "ALLOWED_PUSH_URL_PREFIXES", length = 4000)
	private String allowedPushUrlPrefixes;

	@Column(name = "RATE_LIMIT_PER_SEC")
	private Integer rateLimitPerSec;

	@Column(name = "MAX_CONCURRENT")
	private Integer maxConcurrent;

	@Column(name = "DELETED_AT")
	private Date deletedAt;

	@Override
	public ApiClientInfo info() {
		ApiClientInfo info = new ApiClientInfo();
		info.setClientId(clientId);
		info.setName(name);
		info.setDescription(description);
		info.setEnabled(enabled);
		info.setAllowedOpcodes(split(allowedOpcodes));
		info.setAllowedIps(split(allowedIps));
		info.setAllowedPushUrlPrefixes(split(allowedPushUrlPrefixes));
		info.setRateLimitPerSec(rateLimitPerSec);
		info.setMaxConcurrent(maxConcurrent);
		info.setCreatedBy(createdBy);
		info.setCreatedDate(createdDate);
		info.setUpdatedBy(updatedBy);
		info.setUpdatedDate(updatedDate);
		return info;
	}

	/** clientId 와 사용 여부는 바꾸지 않는다 — 각각 등록 때와 {@code setEnabled} 로 정한다. */
	@Override
	public void from(ApiClientInfo info) {
		this.name = info.getName();
		this.description = info.getDescription();
		this.allowedOpcodes = join(info.getAllowedOpcodes());
		this.allowedIps = join(info.getAllowedIps());
		this.allowedPushUrlPrefixes = join(info.getAllowedPushUrlPrefixes());
		this.rateLimitPerSec = info.getRateLimitPerSec();
		this.maxConcurrent = info.getMaxConcurrent();
	}

	@Override
	public String toPlainText() {
		return clientId;
	}

	static List<String> split(String joined) {
		if (joined == null || joined.isBlank()) {
			return new ArrayList<>();
		}
		return new ArrayList<>(Arrays.stream(joined.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
	}

	static String join(List<String> values) {
		if (values == null) {
			return null;
		}
		List<String> cleaned = values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).distinct().toList();
		return cleaned.isEmpty() ? null : String.join(",", cleaned);
	}
}
