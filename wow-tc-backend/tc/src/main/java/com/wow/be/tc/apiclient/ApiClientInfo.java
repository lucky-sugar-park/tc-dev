package com.wow.be.tc.apiclient;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * tc 를 부르는 타 시스템 하나 (API_CLIENT). 인증은 이 클라이언트에 딸린 API 키로 한다.
 *
 * <p>docs/auth-and-audit-design.md §6.1.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class ApiClientInfo extends AbstractInfo {

	/** 클라이언트 ID. 영문 소문자·숫자·하이픈(예: {@code mes-prod}). 감사 기록의 PRINCIPAL 에 {@code client:{id}} 로 남는다. */
	private String clientId;

	private String name;

	private String description;

	private boolean enabled = true;

	/** 허용 opcode. {@code *} 는 전체, {@code LINE1_*} 처럼 끝에 * 를 붙이면 접두 패턴. 비우면 아무 opcode 도 부를 수 없다. */
	private List<String> allowedOpcodes = new ArrayList<>();

	/** 허용 IP·CIDR(예: {@code 10.0.1.15}, {@code 10.0.1.0/24}). 비우면 제한하지 않는다. */
	private List<String> allowedIps = new ArrayList<>();

	/** 요청의 pushUrl(callback)로 쓸 수 있는 URL 접두어. 비우면 전역 tc.delivery.allowed-url-prefixes 만 따른다. */
	private List<String> allowedPushUrlPrefixes = new ArrayList<>();

	/** 초당 요청 한도. null 이면 전역 기본값(tc.api-client.default-rate-limit-per-sec) */
	private Integer rateLimitPerSec;

	/** 동시 처리 한도(인스턴스당). null 이면 전역 기본값(tc.api-client.default-max-concurrent) */
	private Integer maxConcurrent;

	// ── 조회 전용(저장하지 않음) ──

	/** 키 목록. 단건 조회에서만 채운다. */
	private List<ApiClientKeyInfo> keys;

	/** 활성(폐기·만료되지 않은) 키 수 */
	private int activeKeyCount;

	/** 활성 키 가운데 가장 가까운 만료 시각 */
	private Date nearestExpiresAt;

	/** 이 클라이언트의 키가 마지막으로 쓰인 시각 */
	private Date lastUsedAt;

	@Override
	public String toPlainText() {
		return clientId + " [" + name + (enabled ? "" : ", 중지") + "]";
	}
}
