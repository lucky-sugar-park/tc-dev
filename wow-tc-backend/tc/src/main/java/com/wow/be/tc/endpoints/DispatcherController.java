package com.wow.be.tc.endpoints;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.framework.MesGeneralException;
import com.wow.be.framework.dispatch.MessageCallback;
import com.wow.be.framework.dispatch.MessageDispatcher;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.ResultDeliveryService;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.security.RequestPrincipals;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 타 시스템(MES 등)의 PLC READ/WRITE 요청 입구. 요청 본문은 모두 {@link TcMessageRequest} 형식이다.
 *
 * <ul>
 *   <li>{@code /request} — 비동기. 바로 {@code 202 Accepted} 와 transactionId 를 돌려주고, 결과(성공·실패)는
 *       요청의 callback 주소(pushResult·pushUrl)와 응답 인터페이스의 WebHook 으로 보낸다.</li>
 *   <li>{@code /request-and-reply} — 동기. 결과를 응답 본문으로 돌려주고, 응답 인터페이스의 WebHook 으로도 보낸다.</li>
 * </ul>
 *
 * <p>HTTP 상태
 * <ul>
 *   <li>PLC 와 교환을 마쳤으면 200 이다. PLC 가 오류 종료 코드를 돌려준 경우도 200 이고, 본문의
 *       {@code success=false}, {@code errorCode=ERR_502_TC_PLC_ERROR} 로 알린다.</li>
 *   <li>교환 전·중에 실패하면 errorCode 의 숫자를 HTTP 상태로 쓰고(예: ERR_504_TC_PLC_TIMEOUT → 504),
 *       본문은 실패한 {@link TcMessageResponse} 다(transactionId 포함).</li>
 * </ul>
 */
@Tag(name = "Dispatcher", description = "타 시스템이 HTTP/REST 로 PLC 읽기·쓰기를 요청하는 API")
@RestController
@RequestMapping(value = "/api/v1/ifsvc")
@RequiredArgsConstructor
@Slf4j
// 권한(설계 §4) — PLC 요청은 업무 액션(X). API 클라이언트·관리자·운영자만
@Requires("action.tc.dispatch")
public class DispatcherController {

	private final MessageDispatcher messageDispatcher;

	private final ResultDeliveryService resultDeliveryService;

	@Operation(
		summary = "PLC 읽기·쓰기 비동기 요청",
		description = "바로 202 와 transactionId 를 돌려준다. 결과는 callback 주소(pushResult·pushUrl)와 응답 인터페이스의 WebHook 으로 보낸다."
	)
	@ApiResponse(responseCode = "202", description = "접수됨 — {transactionId, messageId, opcode}")
	@PostMapping(value = "/request")
	public ResponseEntity<Map<String, Object>> service(@RequestBody TcMessageRequest request) throws ToolControlException {
		normalize(request);
		if (!StringUtils.hasText(request.getOpcode())) {
			return ResponseEntity.badRequest().body(accepted(request, "요청에 opcode 가 없습니다."));
		}
		RequestPrincipals.stampAndAuthorize(request);

		messageDispatcher.dispatch(request, new MessageCallback<TcMessageResponse>() {
			@Override
			public void onCompleted(TcMessageResponse response) {
				resultDeliveryService.deliver(resultDeliveryService.targetsOf(request, true), request, response);
			}

			@Override
			public void onError(RequestMessage req, MesGeneralException cause) {
				resultDeliveryService.deliver(resultDeliveryService.targetsOf(request, true), request, failure(request, cause));
			}
		});
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted(request, null));
	}

	@Operation(
		summary = "PLC 읽기·쓰기 동기 요청",
		description = "결과를 응답 본문(TcMessageResponse)으로 돌려준다. 응답 인터페이스의 WebHook 으로도 보낸다."
	)
	@ApiResponse(responseCode = "200", description = "PLC 와 교환을 마침 — 본문의 success 로 성공 여부를 본다")
	@PostMapping(value = "/request-and-reply")
	public ResponseEntity<TcMessageResponse> serviceWithSyncReply(@RequestBody TcMessageRequest request)
			throws ToolControlException {
		normalize(request);
		// 동기 요청은 결과를 응답 본문으로 돌려주고 callback 주소로 보내지 않는다 — 감사에도 주소를 남기지 않게 지운다
		request.setPushResult(false);
		RequestPrincipals.stampAndAuthorize(request);

		TcMessageResponse response;
		int status;
		try {
			response = messageDispatcher.dispatch(request);
			status = HttpStatus.OK.value();
		} catch (MesGeneralException e) {
			response = failure(request, e);
			status = ToolControlException.httpStatusOf(e.getErrorCode());
		}
		resultDeliveryService.deliver(resultDeliveryService.targetsOf(request, false), request, response);
		return ResponseEntity.status(status).body(response);
	}

	/** JSON 에 "timestamp": null 이 오면 요청 시각으로 채운다. */
	private static void normalize(TcMessageRequest request) {
		if (request.getTimestamp() == null) {
			request.setTimestamp(System.currentTimeMillis());
		}
	}

	private static TcMessageResponse failure(TcMessageRequest request, MesGeneralException cause) {
		if (cause.getErrorCode() == null || ToolControlException.httpStatusOf(cause.getErrorCode()) >= 500) {
			log.warn("PLC 요청을 처리하지 못했습니다: opcode={}, transactionId={}, errorCode={}",
				request.getOpcode(), request.getTransactionId(), cause.getErrorCode(), cause);
		}
		TcMessageResponse response = new TcMessageResponse().fillReplyHeader(request);
		return response.fail(cause.getErrorCode(), cause.getMessage());
	}

	private static Map<String, Object> accepted(TcMessageRequest request, String error) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("transactionId", request.getTransactionId());
		body.put("messageId", request.getMessageId());
		body.put("opcode", request.getOpcode());
		if (error != null) {
			body.put("errorCode", ToolControlException.INVALID_REQUEST);
			body.put("message", error);
		}
		return body;
	}
}
