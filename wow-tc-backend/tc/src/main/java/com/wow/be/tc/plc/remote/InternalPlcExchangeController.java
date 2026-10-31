package com.wow.be.tc.plc.remote;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.connector.PlcAdapterManager;
import com.wow.be.tc.plc.connector.ProgrammableControllerAdapter;
import com.wow.be.tc.plc.management.PlcOwnershipManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 다른 인스턴스가 넘긴 PLC 작업을 처리한다(docs/plc-ha-design.md §4.1, docs/plc-protocol-design.md §7·§15). 이 인스턴스가 그 PLC 의
 * 주인일 때만.
 *
 * <pre>
 * POST /internal/plc/{plcName}/execute        — 작업 단위(4단계부터)
 * 본문: { "op": "READ"|"WRITE"|"RAW", "protocol": "MODBUS_TCP", "settings": {...}, "points": [...], "values": {...},
 *        "frame": "&lt;base64, RAW 만&gt;", "reqId": "...", "timeoutMs": 20000 }
 * 응답: 200 { "values": {...}, "status": { "protocol", "code", "text" } } | RAW 면 200 { "frame": "&lt;base64&gt;" }
 *
 * POST /internal/plc/{plcName}/exchange       — 프레임 바이트(3단계까지). 순차 재기동 중 옛 인스턴스가 보내므로 계속 받는다
 * 본문: { "frame": "&lt;base64&gt;", "timeoutMs": 20000, "reqId": "..." } → 200 { "frame": "&lt;base64&gt;" }
 *
 * 헤더: X-TC-Instance, X-TC-Timestamp, X-TC-Signature(HMAC)
 * 오류: 401 서명 | 404 소유권 관리 꺼짐 | 409 ERR_409_TC_NOT_OWNER | 400 | 409 ERR_409_TC_INTERFACE_CONFIG | 503 | 504
 * </pre>
 * 작업 단위면 점 묶기·응답 검증을 주인이 한다 — OPC UA 처럼 넘길 바이트가 없는 프로토콜도 담는다. 점 정의와 커넥터 설정은 요청을 받은
 * 인스턴스가 보낸 것을 쓴다(인스턴스 사이 설정이 잠깐 달라도 요청을 받은 쪽 정의대로).
 *
 * <p>사용자 인증·감사·사용량 제한을 거치지 않는다 — 그것은 요청을 받은 인스턴스가 이미 했다.
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
		return handle(plcName, instance, timestamp, sig, body, (adapter, request) -> raw(adapter, request));
	}

	@PostMapping("/internal/plc/{plcName}/execute")
	public ResponseEntity<Map<String, Object>> execute(@PathVariable("plcName") String plcName,
			@RequestHeader(value = InternalSignature.HEADER_INSTANCE, required = false) String instance,
			@RequestHeader(value = InternalSignature.HEADER_TIMESTAMP, required = false) String timestamp,
			@RequestHeader(value = InternalSignature.HEADER_SIGNATURE, required = false) String sig,
			@RequestBody(required = false) byte[] body) {
		return handle(plcName, instance, timestamp, sig, body, (adapter, request) -> {
			String op = request.get("op") instanceof String o ? o : "";
			if ("RAW".equals(op)) {
				return raw(adapter, request);
			}
			PlcOperation.Kind kind;
			try {
				kind = PlcOperation.Kind.valueOf(op);
			} catch (IllegalArgumentException e) {
				return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "op 은 READ·WRITE·RAW 입니다: " + op);
			}
			List<PointInfo> points;
			Map<String, Object> settings;
			Map<String, Object> values;
			try {
				points = JSON.convertValue(request.get("points"), new TypeReference<List<PointInfo>>() { });
				settings = JSON.convertValue(request.get("settings"), new TypeReference<Map<String, Object>>() { });
				values = JSON.convertValue(request.get("values"), new TypeReference<Map<String, Object>>() { });
			} catch (IllegalArgumentException e) {
				return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "points·settings·values 를 읽을 수 없습니다.");
			}
			if (points == null || points.isEmpty() || !(request.get("protocol") instanceof String protocol)) {
				return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "protocol·points 가 없습니다.");
			}
			String reqId = request.get("reqId") instanceof String r ? r : "-";
			PlcOperation operation = new PlcOperation(protocol, settings != null ? settings : Map.of(), kind, points,
				values != null ? values : Map.of(), reqId);
			PlcResult result = adapter.execute(operation);
			Map<String, Object> answer = new LinkedHashMap<>();
			answer.put("values", result.values());
			answer.put("status", result.status());
			return ResponseEntity.ok(answer);
		});
	}

	/** 프레임 하나를 주고받는다 — /exchange 와 /execute 의 RAW */
	private static ResponseEntity<Map<String, Object>> raw(ProgrammableControllerAdapter adapter, Map<String, Object> request)
			throws ToolControlException {
		if (!(request.get("frame") instanceof String frame)) {
			return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "frame 이 없습니다.");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(frame);
		} catch (IllegalArgumentException e) {
			return error(HttpStatus.BAD_REQUEST, ToolControlException.INVALID_REQUEST, "frame 이 Base64 가 아닙니다.");
		}
		String reqId = request.get("reqId") instanceof String r ? r : "-";
		byte[] answer = adapter.exchangeFrame(reqId, bytes);
		return ResponseEntity.ok(Map.of("frame", Base64.getEncoder().encodeToString(answer)));
	}

	@FunctionalInterface
	private interface Work {
		ResponseEntity<Map<String, Object>> run(ProgrammableControllerAdapter adapter, Map<String, Object> request) throws ToolControlException;
	}

	/** 소유권 관리 켜짐·서명·주인 확인 뒤 일을 한다. PLC 오류는 그 코드의 HTTP 상태로 */
	private ResponseEntity<Map<String, Object>> handle(String plcName, String instance, String timestamp, String sig, byte[] body, Work work) {
		if (!ownership.isEnabled()) {
			return error(HttpStatus.NOT_FOUND, "ERR_404", "PLC 소유권 관리가 꺼져 있습니다.");
		}
		byte[] raw = body != null ? body : new byte[0];
		if (!signature.verify(plcName, timestamp, sig, raw)) {
			log.warn("서명이 맞지 않는 내부 PLC 요청을 거절합니다: plc={}, from={}", plcName, instance);
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
		try {
			return work.run(adapter, request);
		} catch (ToolControlException e) {
			return error(HttpStatus.valueOf(ToolControlException.httpStatusOf(e.getErrorCode())), e.getErrorCode(), e.getMessage());
		}
	}

	private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("errorCode", code);
		body.put("message", message);
		return ResponseEntity.status(status).body(body);
	}
}
