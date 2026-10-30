package com.wow.be.tc.dispatch;

/**
 * opcode 별 마지막 PLC 응답(원본 바이트)을 기억해, 새 응답이 달라졌는지 판단한다.
 */
public interface PlcResponseService {

	/**
	 * 직전 응답과 비교하고, 달라졌으면 새 응답을 기억한다.
	 *
	 * <p>기억해 둔 응답이 없거나(첫 응답) 저장소를 쓸 수 없으면 "바뀜"으로 본다 — 변경 감지 실패가 PLC 요청을
	 * 실패시키지 않게 한다.
	 *
	 * @param opcode   인터페이스 이름
	 * @param original PLC 응답 원본 바이트 (null 이면 바뀜으로 본다)
	 * @return 달라졌으면 true
	 */
	boolean checkChangedAndRemember(String opcode, byte[] original);
}
