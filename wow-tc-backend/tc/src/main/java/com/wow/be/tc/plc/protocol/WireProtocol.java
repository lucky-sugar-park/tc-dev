package com.wow.be.tc.plc.protocol;

/**
 * TCP 위의 PLC 프로토콜이 연결 계층에 알려 주는 것 (docs/plc-protocol-design.md §14) — 응답을 어디서 자르는지, 요청과 응답을
 * 짝지을 번호가 있는지, 쉬는 연결을 어떻게 확인하는지. TCP 어댑터·연결 시험이 프로토콜을 모르고 이것만 쓴다.
 * 드라이버가 커넥터 설정으로 만든다({@link PlcDriver#wire}).
 */
public interface WireProtocol {

	/** 받은 바이트의 앞에서 응답 프레임 하나의 길이. 아직 덜 왔으면 0, 응답 머리가 아니면 -1(버린다) */
	int responseLength(byte[] buf, int available);

	/**
	 * 이 요청에 짝지을 번호(일련번호·트랜잭션 ID)를 넣을 수 있는가. 그러면 시간 초과에도 연결을 끊지 않는다 — 늦게 온 응답은
	 * 번호로 버린다. MELSEC 4E·Modbus TCP 는 true, MELSEC 3E 는 false.
	 */
	boolean hasSerial(byte[] request);

	/** 요청에 번호를 넣는다(1~0xFFFF) */
	void setSerial(byte[] request, int serial);

	/** 응답의 번호. 번호가 없는 응답이면 -1 */
	int serialOf(byte[] response);

	/** 쉬는 연결 확인 요청 — MELSEC 루프백(0619), Modbus 레지스터 하나 읽기 */
	byte[] healthCheckRequest();

	/** 연결 확인 응답이 PLC 가 살아 있다는 뜻인가 */
	boolean healthCheckOk(byte[] response);
}
