package com.wow.be.tc.delivery;

import com.wow.be.tc.delivery.DeliveryDefinitionInfo.DeliverOn;
import com.wow.be.tc.dispatch.TcMessageResponse;

/**
 * 결과를 보낼 곳 하나와 보낼 조건.
 *
 * @param type         전달 유형
 * @param name         대상 이름 (WebHook 이름, callback 이면 "callback", DB_TABLE 이면 DB 연결 이름)
 * @param url          보낼 주소 (DB_TABLE 이면 null)
 * @param deliverOn    성공·실패 중 언제 보낼지
 * @param onChangeOnly PLC 응답이 직전과 달라졌을 때만 보낼지
 * @param table        DB_TABLE 의 테이블 이름 (그 밖에는 null)
 */
public record DeliveryTarget(DeliveryType type, String name, String url, DeliverOn deliverOn, boolean onChangeOnly, String table) {

	public enum DeliveryType {
		/** 요청이 지정한 return url 로 PUT (레거시 pushUrl) */
		CALLBACK_URL,
		/** 응답 인터페이스에 등록된 WebHook 으로 POST */
		WEBHOOK,
		/** 등록한 DB 연결의 테이블에 한 행 INSERT (docs/db-table-delivery.md) */
		DB_TABLE
	}

	public DeliveryTarget(DeliveryType type, String name, String url, DeliverOn deliverOn, boolean onChangeOnly) {
		this(type, name, url, deliverOn, onChangeOnly, null);
	}

	/** 조건 없이 늘 보내는 대상 (레거시 방식) */
	public static DeliveryTarget always(DeliveryType type, String name, String url) {
		return new DeliveryTarget(type, name, url, DeliverOn.ALL, false);
	}

	/** 감사·로그에 남길 대상 표기 — 주소 또는 테이블 */
	public String destination() {
		return url != null ? url : table;
	}

	/** 이 응답을 이 대상에 보내야 하는가 */
	public boolean accepts(TcMessageResponse response) {
		boolean success = !Boolean.FALSE.equals(response.getSuccess());
		if (deliverOn == DeliverOn.SUCCESS && !success) {
			return false;
		}
		if (deliverOn == DeliverOn.FAILURE && success) {
			return false;
		}
		// 실패 응답은 PLC 값 비교 대상이 아니다 — 변경 조건은 성공 응답에만 건다
		return !(onChangeOnly && success && !response.isChanged());
	}
}
