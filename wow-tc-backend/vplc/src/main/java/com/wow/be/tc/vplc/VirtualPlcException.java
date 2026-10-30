package com.wow.be.tc.vplc;

import com.wow.be.framework.MesGeneralException;

/**
 * 가상 PLC 모듈 예외.
 *
 * <p>framework {@link MesGeneralException}(검사 예외 + errorCode)을 상속한다. 레거시 VirtualPlcException 도 검사 예외였으므로
 * 서비스·컨트롤러의 {@code throws} 선언은 그대로 둔다. errorCode 는 전역 예외 처리기
 * ({@code endpoints.GlobalExceptionHandler})가 HTTP 상태로 바꾸는 기준이다.
 */
public class VirtualPlcException extends MesGeneralException {

	private static final long serialVersionUID = 1L;

	/** 잘못된 요청(필수값 누락 등) → 400 */
	public static final String ERR_400 = "ERR_400";

	/** 대상 없음 → 404 */
	public static final String ERR_404 = "ERR_404";

	/** 지금 상태로는 할 수 없음 → 409 */
	public static final String ERR_409 = "ERR_409";

	/** 서버 내부 오류 → 500 */
	public static final String ERR_500 = "ERR_500";

	public VirtualPlcException(String errorCode, String message) {
		super(errorCode, message);
	}

	public VirtualPlcException(String errorCode, String message, Throwable cause) {
		super(errorCode, message, cause);
	}
}
