package com.wow.be.tc.plc.connector;

import java.util.Properties;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.protocol.PlcDrivers;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;

public interface ProgrammableControllerAdapter {
	
	public abstract long getId();

	public abstract String getName();
	
	public abstract void ready(long id, String name, Properties props) throws ToolControlException;
	
	public abstract void pause();
//	
	public abstract void resume();
//	
	public abstract void stop();
//	
	public abstract void start() throws ToolControlException;
	
	public abstract void destroy();

	/**
	 * 앱 종료용 — 연결만 닫고 이벤트를 내지 않는다. 커넥터의 DB 상태(PUBLISHED)를 그대로 두어 다음 기동 때 다시 올라오게
	 * 하고, PLC 쪽 연결 슬롯을 빨리 비워 다음 기동(이나 다른 인스턴스)이 바로 연결할 수 있게 한다.
	 */
	public abstract void shutdownQuietly();
	
	/**
	 * 요청 프레임 하나를 PLC 와 주고받는다 — 다른 인스턴스가 넘긴 교환(내부 전달)을 소유 인스턴스가 처리하는 길
	 * (docs/plc-ha-design.md §3.1). 프레임을 만들고 해석하는 일은 프로토콜 드라이버가 한다.
	 *
	 * @return 응답 프레임
	 */
	public abstract byte[] exchangeFrame(String reqId, byte[] frame) throws ToolControlException;

	/**
	 * 점 읽기·쓰기 — 프로토콜 드라이버가 프레임을 만들고 해석하고, 교환은 이 어댑터의
	 * {@link #exchangeFrame}(로컬 채널 또는 주인 인스턴스)로 한다(docs/plc-protocol-design.md §3·§5).
	 */
	public default PlcResult execute(PlcOperation operation) throws ToolControlException {
		return PlcDrivers.of(operation.protocol()).execute(operation, this::exchangeFrame);
	}

	/** 이 인스턴스가 PLC 와 직접 연결한 어댑터인가. 원격 어댑터(다른 인스턴스로 넘김)는 false */
	public default boolean isLocal() {
		return true;
	}
	
	public abstract boolean testConnection() throws ToolControlException;
	
	/** 지금 운영 상태·살아 있는 채널 수·마지막 연결 오류 */
	public abstract AdapterStatus status();
}
