package com.wow.be.tc.plc.protocol;

import java.util.Map;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;

/**
 * 프로토콜 드라이버 (docs/plc-protocol-design.md §5). 상태가 없다 — 프레임을 만들고 해석하는 일만 하고, 연결은
 * {@link FrameExchanger} 로 받는다. 그래서 로컬 어댑터와 원격 어댑터(다중화)가 같은 드라이버를 쓴다.
 */
public interface PlcDriver {

	/** {@code MELSEC_MC}, {@code MODBUS_TCP}, {@code OPC_UA} */
	String protocol();

	/**
	 * 커넥터 설정을 검증하고 기본값을 채운다. 잘못되면 {@link IllegalArgumentException}(400).
	 *
	 * @param settings 받은 설정(없으면 빈 맵)
	 * @param legacyFrameFormat 옛 커넥터 필드 {@code messageFrameFormat}(BINARY·ASCII) — 설정에 프레임이 없을 때
	 */
	Map<String, Object> normalizeSettings(Map<String, Object> settings, String legacyFrameFormat);

	/** 점 하나를 검증한다(주소·형·길이·읽기/쓰기 가능). 잘못되면 {@link IllegalArgumentException}(400) */
	void validatePoint(PointInfo point, InterfaceType interfaceType);

	/** 작업을 한다. 쓰기 값이 빠졌거나 잘못되면 {@code ERR_400} */
	PlcResult execute(PlcOperation operation, FrameExchanger exchanger) throws ToolControlException;

	/** TCP 연결 계층에 알려 줄 전송 규약(응답 자르기·요청 번호·연결 확인) — 정규화한 설정으로 만든다 */
	WireProtocol wire(Map<String, Object> settings);

	/** 화면용 설명 — 설정 항목·주소 영역·예 */
	ProtocolDescriptor describe();
}
