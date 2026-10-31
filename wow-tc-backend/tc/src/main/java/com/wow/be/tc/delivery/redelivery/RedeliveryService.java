package com.wow.be.tc.delivery.redelivery;

import java.util.List;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;

/**
 * 관리자 재전송 (docs/redelivery-design.md). 송수신 감사 기록을 골라 이미 나간 결과를 성공·실패와 관계없이 다시 보낸다.
 *
 * <p>검증만 요청 스레드에서 하고, 보내기는 전달 스레드 풀에서 대상마다 원래 시각 순으로 하나씩 한다. 결과는 DELIVERY 감사로 본다.
 */
public interface RedeliveryService {

	/**
	 * 전달 기록(DELIVERY)을 같은 대상으로 다시 보낸다. 대상의 지금 설정(허용 주소, WebHook 지금 주소, DB 연결 사용 여부)을 다시 확인한다.
	 *
	 * @throws ToolControlException 목록이 비었거나 한 번에 보낼 수를 넘을 때(ERR_400)
	 */
	Result redeliverDeliveries(List<Long> auditIds) throws ToolControlException;

	/**
	 * 응답 기록(RESPONSE, ERROR)을 그 인터페이스의 지금 전달 대상 중 고른 곳으로 보낸다. 전달 조건(deliverOn, onChangeOnly)은 보지 않는다.
	 *
	 * @throws ToolControlException 목록이 비었거나 한 번에 보낼 수를 넘을 때(ERR_400)
	 */
	Result redeliverResponses(List<Long> auditIds, List<TargetRef> targets) throws ToolControlException;

	/** 보낼 대상 — 유형, 이름(WebHook 이름·DB 연결 이름), DB_TABLE 이면 테이블 */
	record TargetRef(DeliveryType deliveryType, String targetName, String tableName) {

		public String label() {
			return deliveryType + ":" + targetName + (tableName != null ? ":" + tableName : "");
		}
	}

	record DeliveriesRequest(List<Long> auditIds) {
	}

	record ResponsesRequest(List<Long> auditIds, List<TargetRef> targets) {
	}

	/** 검증에서 걸려 보내지 않은 건. target 은 응답 기록으로 보낼 때만 */
	record Rejected(Long auditId, String target, String reason) {
	}

	/** @param accepted 보내기로 한 건수(대상 × 기록) */
	record Result(int accepted, List<Rejected> rejected) {
	}
}
