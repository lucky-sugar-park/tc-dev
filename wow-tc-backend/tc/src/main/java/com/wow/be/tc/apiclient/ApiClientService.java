package com.wow.be.tc.apiclient;

import java.util.List;
import java.util.Optional;

import com.wow.be.tc.ToolControlException;

/**
 * API 클라이언트·키 관리와 키 검증. docs/auth-and-audit-design.md §6.
 *
 * <p>모든 변경은 런타임에 반영된다(재기동 없음). 같은 인스턴스의 검증 캐시는 변경 즉시 지운다.
 */
public interface ApiClientService {

	List<ApiClientInfo> searchAll();

	/** 단건. 키 목록을 함께 채운다. 없으면 ERR_404. */
	ApiClientInfo find(String clientId) throws ToolControlException;

	ApiClientInfo register(ApiClientInfo info) throws ToolControlException;

	/** 이름·설명·허용 범위·사용량 한도를 바꾼다. 사용 여부는 {@link #setEnabled} 로 바꾼다. */
	ApiClientInfo update(ApiClientInfo info) throws ToolControlException;

	ApiClientInfo setEnabled(String clientId, boolean enabled) throws ToolControlException;

	/** 삭제. 활성 키가 있으면 ERR_409. 삭제한 clientId 는 다시 쓸 수 없다. */
	void delete(String clientId) throws ToolControlException;

	/**
	 * 새 키를 발급한다. 활성 키가 이미 2개면 ERR_409.
	 *
	 * @param expiresInDays null 이면 기본 수명(tc.api-client.default-key-days), 최대 tc.api-client.max-key-days
	 */
	IssuedApiKey issueKey(String clientId, Integer expiresInDays) throws ToolControlException;

	ApiClientKeyInfo revokeKey(String clientId, String keyId) throws ToolControlException;

	/**
	 * 키 문자열을 검증한다. 형식·없는 키·해시 불일치·폐기·만료·클라이언트 중지는 모두 빈 값이다(사유는 로그에만 남긴다).
	 * 성공하면 마지막 사용 시각을 남긴다(1분에 한 번).
	 */
	Optional<VerifiedApiClient> verify(String apiKey, String remoteAddress);

	/** 만료가 {@code withinDays} 일 안에 다가온 활성 키 */
	List<ApiClientKeyInfo> findKeysExpiringWithin(int withinDays);
}
