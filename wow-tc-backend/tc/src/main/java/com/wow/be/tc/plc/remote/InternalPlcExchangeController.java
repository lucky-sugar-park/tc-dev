package com.wow.be.tc.plc.remote;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.framework.security.authz.PublicApi;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.management.PlcOwnershipManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 인스턴스가 넘긴 PLC 교환을 처리한다(docs/plc-ha-design.md §4.1). 이 인스턴스가 그 PLC 의 주인일 때만.
 *
 * <pre>
 * POST /internal/plc/{plcName}/exchange
 * 헤더: X-TC-Instance, X-TC-Timestamp, X-TC-Signature(HMAC)
 * 본문: { "frame": "&lt;base64&gt;", "timeoutMs": 20000, "reqId": "..." }
 * 응답: 200 { "frame": "&lt;base64&gt;" } | 401 서명 | 404 소유권 관리 꺼짐 | 409 ERR_409_TC_NOT_OWNER | 503 | 504
 * </pre>
 * 사용자 인증·감사·사용량 제한을 거치지 않는다 — 그것은 요청을 받은 인스턴스가 이미 했다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PublicApi
public class InternalPlcExchangeController {

	private static final ObjectMapper JSON = new ObjectMapper();

	private final InternalSignature signature;

	private final PlcOwnershipManager ownership;

	@PostMapping("/internal/plc/{plcName}/exchange")
	public ResponseEntity<Map<String, Object>> exchange(@PathVariable("plcName") String plcName,
			@RequestHeader(value = InternalSignature.HEADER_INSTANCE, required = false) String instance,
			@RequestHeader(value = InternalSignature.HEADER_TIMESTAMP, required = false) String timestamp,
			@RequestHeader(value = InternalSignature.HEADER_SIGNATURE, required = false) String sig,
			@RequestBody(required = false) byte[] body) {
		if (!ownership.isEnabled()) {
			return error(HttpStatus.NOT_FOUND, "ERR_404", "PLC 소유권 관리가 꺼져 있습니다.");
		}
		byte[] raw = body != null ? body : new byte[0];
		if (!signature.verify(plcName, timestamp, sig, raw)) {
			log.warn("서명이 맞지 않는 내부 PLC 교환 요청을 거절합니다: plc={}, from={}", plcName, instance);
			return error(HttpStatus.UNAUTHORIZED, "ERR_401", "내부 요청 서명이 맞지 않습니다.");
		}
		ProgrammableControllerAdapter adapter = PlcAdapterManager.getPlcManager().get(plcName);
		if (adapter == null || !adapter.isLocal() || !ownership.owned().contains(plcName)) {
			return error(HttpStatus.CONFLICT, RemotePlcAdapter.NOT_OWNER, "이 인스턴스는 PLC " + plcName + " 의 주인이 아닙니다.");
		}
		Map<String, Object> request;
		try {
			request = JSON.readValue(raw, new TypeReference<Map<String, Object>>() { });
		} catch (IOException e) {
			return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "본문을 읽을 수 없습니다.");
		}
		if (!(request.get("frame") instanceof String frame)) {
			return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "frame 이 없습니다.");
		}
		String reqId = request.get("reqId") instanceof String r ? r : "-";
		try {
			byte[] answer = adapter.exchangeFrame(reqId, Base64.getDecoder().decode(frame));
			return ResponseEntity.ok(Map.of("frame", Base64.getEncoder().encodeToString(answer)));
		} catch (ToolControlException e) {
			return error(HttpStatus.valueOf(ToolControlException.httpStatusOf(e.getErrorCode())), e.getErrorCode(), e.getMessage());
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "frame 이 Base64 가 아닙니다.");
		}
	}

	private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("errorCode", code);
		body.put("message", message);
		return ResponseEntity.status(status).body(body);
	}
}
