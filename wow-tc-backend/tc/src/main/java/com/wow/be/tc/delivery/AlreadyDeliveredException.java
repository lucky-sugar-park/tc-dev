package com.wow.be.tc.delivery;

/**
 * 재전송한 결과를 받는 쪽이 이미 가지고 있다 — DB_TABLE 의 유니크 키 중복 등. 실패가 아니라 "이미 있음" 으로 기록한다
 * (docs/redelivery-design.md R6).
 */
public class AlreadyDeliveredException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public AlreadyDeliveredException(String message, Throwable cause) {
		super(message, cause);
	}
}
