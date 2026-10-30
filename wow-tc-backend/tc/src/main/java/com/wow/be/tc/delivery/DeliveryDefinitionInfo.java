package com.wow.be.tc.delivery;

import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 인터페이스 하나의 결과 전달 정의 한 줄 (INTERFACE_DELIVERY).
 *
 * <p>한 인터페이스에 정의가 하나라도 있으면 그 정의대로만 보낸다. 하나도 없으면 레거시 방식
 * (요청의 callback + 응답 인터페이스의 WebHook 목록)을 쓴다.
 */
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class DeliveryDefinitionInfo extends AbstractInfo {

	/**
	 * 정의 ID. 영문 소문자와 점(.) 계층, 전역 유일. 비워서 등록하면
	 * {@code {인터페이스 이름}.{유형}.{대상 이름}} 을 소문자로 만들어 쓴다 (예: {@code line1.temp.read.webhook.mes.temp}).
	 */
	private String id;

	/** 요청 인터페이스 이름(opcode) */
	private String interfaceName;

	private DeliveryType deliveryType;

	/** 대상 이름 — WEBHOOK 이면 WebHook 이름, DB_TABLE 이면 DB 연결 이름(tc 자체 DB 는 {@code tc}). CALLBACK_URL 은 비운다(주소는 요청이 준다). */
	private String targetName;

	/** DB_TABLE 의 테이블 이름(스키마를 붙이려면 {@code schema.table}). 표준 칼럼을 가져야 한다 — docs/db-table-delivery.md */
	private String tableName;

	/** 전달 시점 — 성공/실패 상관없이, 성공일 때만, 실패일 때만 */
	private DeliverOn deliverOn = DeliverOn.ALL;

	/** true 면 PLC 응답 원본이 직전과 달라졌을 때만 보낸다 (스케줄 폴링에서 같은 값을 반복해 보내지 않게) */
	private boolean onChangeOnly;

	private boolean use = true;

	private String description;

	public enum DeliverOn {
		ALL, SUCCESS, FAILURE
	}

	@Override
	public String toPlainText() {
		return id + " [" + interfaceName + " → " + deliveryType + (targetName != null ? ":" + targetName : "")
			+ (tableName != null ? "/" + tableName : "") + "]";
	}
}
