package com.wow.be.tc.endpoints;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.redelivery.RedeliveryService;
import com.wow.be.tc.delivery.redelivery.RedeliveryService.DeliveriesRequest;
import com.wow.be.tc.delivery.redelivery.RedeliveryService.Result;
import com.wow.be.tc.delivery.redelivery.RedeliveryService.ResponsesRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 결과 재전송 API (docs/redelivery-design.md §5). 고르기는 감사 검색({@code /api/v1/ifsvc/audit/search/page})으로 한다.
 *
 * <p>검증에서 걸린 건은 {@code rejected} 로 돌려주고 나머지만 보낸다. 보내기는 뒤에서 하므로 결과는 DELIVERY 감사로 본다.
 * 한 건도 보내지 못하면 400(같은 본문)이다.
 */
@Tag(name = "Redelivery", description = "송수신 감사 기록을 골라 결과를 다시 보낸다(성공·실패 무관)")
@RestController
@RequestMapping("/api/v1/ifsvc/redelivery")
@RequiredArgsConstructor
// 권한(설계 R7) — 업무 액션(X). 기본은 관리자만
@Requires("action.tc.redeliver")
public class RedeliveryController {

	private final RedeliveryService redeliveryService;

	@Operation(summary = "전달 기록 재전송", description = "DELIVERY 감사 기록을 같은 대상(지금 설정)으로 다시 보낸다.")
	@ApiResponse(responseCode = "202", description = "{accepted, rejected[]}")
	@PostMapping("/deliveries")
	public ResponseEntity<Result> deliveries(@RequestBody DeliveriesRequest request) throws ToolControlException {
		return respond(redeliveryService.redeliverDeliveries(request.auditIds()));
	}

	@Operation(summary = "응답 기록을 골라 보내기",
		description = "RESPONSE·ERROR 감사 기록을 그 인터페이스의 지금 전달 대상 중 고른 곳으로 보낸다. 전달 조건은 보지 않는다.")
	@ApiResponse(responseCode = "202", description = "{accepted, rejected[]}")
	@PostMapping("/responses")
	public ResponseEntity<Result> responses(@RequestBody ResponsesRequest request) throws ToolControlException {
		return respond(redeliveryService.redeliverResponses(request.auditIds(), request.targets()));
	}

	private static ResponseEntity<Result> respond(Result result) {
		return ResponseEntity.status(result.accepted() > 0 ? HttpStatus.ACCEPTED : HttpStatus.BAD_REQUEST).body(result);
	}
}
