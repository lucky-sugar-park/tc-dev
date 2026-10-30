package com.wow.be.tc.endpoints;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.apiclient.ApiClientInfo;
import com.wow.be.tc.apiclient.ApiClientKeyInfo;
import com.wow.be.tc.apiclient.ApiClientService;
import com.wow.be.tc.apiclient.IssuedApiKey;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * API 클라이언트·키 관리 API (설계 §6.6). 관리자 전용이다(자원 tc.apiclient 은 관리자만 — TcPermissionChecker).
 * 모든 변경은 재기동 없이 바로 반영된다.
 */
@Tag(name = "API Client", description = "타 시스템(API 클라이언트)과 API 키 관리 — 관리자 전용")
@RestController
@RequestMapping("/api/v1/api-clients")
@RequiredArgsConstructor
@Requires("tc.apiclient")
public class ApiClientManagementController {

	private final ApiClientService apiClientService;

	/** 키 발급 요청 본문. expiresInDays 를 비우면 기본 수명 */
	public record IssueKeyRequest(Integer expiresInDays) {
	}

	@Operation(summary = "클라이언트 목록", description = "활성 키 수, 가장 가까운 만료일, 마지막 사용 시각을 함께 준다.")
	@Requires(action = 'R')
	@GetMapping
	public List<ApiClientInfo> search() throws ToolControlException {
		return apiClientService.searchAll();
	}

	@Operation(summary = "클라이언트 한 건", description = "키 목록을 함께 준다. 키 값과 해시는 주지 않는다.")
	@Requires(action = 'R')
	@GetMapping("/{clientId}")
	public ApiClientInfo find(@PathVariable("clientId") String clientId) throws ToolControlException {
		return apiClientService.find(clientId);
	}

	@Operation(summary = "클라이언트 등록")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiClientInfo register(@RequestBody ApiClientInfo info) throws ToolControlException {
		return apiClientService.register(info);
	}

	@Operation(summary = "클라이언트 수정", description = "이름·설명·허용 opcode·IP·pushUrl·사용량 한도. 사용 여부는 enable/disable 로 바꾼다.")
	@PutMapping("/{clientId}")
	public ApiClientInfo update(@PathVariable("clientId") String clientId, @RequestBody ApiClientInfo info)
			throws ToolControlException {
		info.setClientId(clientId);
		return apiClientService.update(info);
	}

	@Operation(summary = "클라이언트 사용 재개")
	@PostMapping("/{clientId}/enable")
	public ApiClientInfo enable(@PathVariable("clientId") String clientId) throws ToolControlException {
		return apiClientService.setEnabled(clientId, true);
	}

	@Operation(summary = "클라이언트 사용 중지", description = "이 클라이언트의 모든 키가 바로 거부된다.")
	@PostMapping("/{clientId}/disable")
	public ApiClientInfo disable(@PathVariable("clientId") String clientId) throws ToolControlException {
		return apiClientService.setEnabled(clientId, false);
	}

	@Operation(summary = "클라이언트 삭제", description = "활성 키가 있으면 409. 삭제한 clientId 는 다시 쓸 수 없다. 운영에서는 중지를 권장한다.")
	@DeleteMapping("/{clientId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable("clientId") String clientId) throws ToolControlException {
		apiClientService.delete(clientId);
	}

	@Operation(summary = "키 발급", description = "응답의 apiKey 는 이번 한 번만 보인다. 활성 키가 이미 2개면 409.")
	@PostMapping("/{clientId}/keys")
	@ResponseStatus(HttpStatus.CREATED)
	public IssuedApiKey issueKey(@PathVariable("clientId") String clientId,
			@RequestBody(required = false) IssueKeyRequest body) throws ToolControlException {
		return apiClientService.issueKey(clientId, body != null ? body.expiresInDays() : null);
	}

	@Operation(summary = "키 폐기")
	@PostMapping("/{clientId}/keys/{keyId}/revoke")
	public ApiClientKeyInfo revokeKey(@PathVariable("clientId") String clientId, @PathVariable("keyId") String keyId)
			throws ToolControlException {
		return apiClientService.revokeKey(clientId, keyId);
	}
}
