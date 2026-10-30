package com.wow.be.tc.plc.connector;

import java.util.Properties;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.PlcReqAndResHolder;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcRequestCallback;
import com.wow.be.tc.plc.PlcResponse;

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
	
	public abstract void pauseAsyncUse();
	
	public abstract void resumeAsyncUse();
	
	public abstract void destroy();

	/**
	 * 앱 종료용 — 연결만 닫고 이벤트를 내지 않는다. 커넥터의 DB 상태(PUBLISHED)를 그대로 두어 다음 기동 때 다시 올라오게
	 * 하고, PLC 쪽 연결 슬롯을 빨리 비워 다음 기동(이나 다른 인스턴스)이 바로 연결할 수 있게 한다.
	 */
	public abstract void shutdownQuietly();
	
	/**
	 * PLC READ/WRITE 동기식으로 요청 
	 */
	public abstract void request(PlcRequest req, PlcResponse res) throws ToolControlException;
	
	/**
	 * PLC READ/WRITE 비동기식으로 요청
	 */
	public abstract void request(PlcReqAndResHolder reqAndResHolder, PlcRequestCallback reqCallback) throws ToolControlException;
	
	public abstract boolean testConnection() throws ToolControlException;
	
	public abstract PlcRequest newPlcRequestInstance();
	
	public abstract PlcResponse newPlcResponseInstance();	
}
