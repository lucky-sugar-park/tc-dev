package com.wow.be.tc.vplc.core;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;

/**
 * 메모리에 올라온(게시된) 가상 PLC 하나 — 종류(MELSEC MC·Modbus TCP·OPC UA)와 관계없이 같은 상태 단계로 다룬다(docs/vplc-architecture.md).
 * 게시 = 이 객체를 만든다, 시작 = 포트를 연다, 정지 = 포트를 닫는다, 일시정지 = 연결은 두고 답하지 않는다, 해제 = 정지하고 버린다.
 * 상태 규칙은 {@link AbstractVirtualPlc} 한 곳에 있다. 메모리는 능력 인터페이스({@code core.memory})로 따로.
 */
public interface VirtualPlc {

	String getId();

	String getName();

	Protocol getProtocol();

	PlcStatus getStatus();

	void start() throws VirtualPlcException;

	void stop() throws VirtualPlcException;

	void pause() throws VirtualPlcException;

	void resume() throws VirtualPlcException;

	void release() throws VirtualPlcException;

	/** 붙어 있는 연결을 모두 끊는다(포트는 계속 받는다) — tc 재연결 시험. 끊은 수 */
	int dropConnections();
}
