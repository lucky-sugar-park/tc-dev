package com.wow.be.tc.plc.connector.melsec;

import com.wow.be.tc.plc.connector.tcp.TcpPlcAdapter;
import com.wow.be.tc.plc.protocol.melsec.MelsecDriver;

/**
 * MELSEC MC 프로토콜(3E·4E 프레임) 어댑터. 연결·재연결·시간 초과·연결 확인(루프백)은 {@link TcpPlcAdapter}, 프레임은
 * {@link MelsecDriver}·{@code McFrames}.
 *
 * <p>DB 의 커넥터 {@code adapterClassName} 이 이 클래스 이름을 들고 있어 이름을 바꾸지 않는다.
 */
public class MelsecProgrammableControllerAdapter extends TcpPlcAdapter {

	@Override
	protected String protocol() {
		return MelsecDriver.PROTOCOL;
	}
}
