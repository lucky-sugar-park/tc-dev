package com.wow.be.tc.plc.connector.modbus;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.PlcRequest;
import com.wow.be.tc.plc.PlcResponse;
import com.wow.be.tc.plc.connector.melsec.MelsecPlcRequest;
import com.wow.be.tc.plc.connector.melsec.MelsecPlcResponse;
import com.wow.be.tc.plc.connector.tcp.TcpPlcAdapter;
import com.wow.be.tc.plc.protocol.modbus.ModbusDriver;

/**
 * Modbus TCP 어댑터 (docs/plc-protocol-design.md §14). 연결·재연결·시간 초과·연결 확인은 {@link TcpPlcAdapter}, 프레임은
 * {@link ModbusDriver}. POINTS 인터페이스만 받는다 — RAW(MC 헤더·데이터 바이트 정의)는 MELSEC 전용이다.
 * 요청마다 트랜잭션 ID 가 있어 시간 초과에도 연결을 끊지 않는다.
 */
public class ModbusTcpAdapter extends TcpPlcAdapter {

	@Override
	protected String protocol() {
		return ModbusDriver.PROTOCOL;
	}

	@Override
	public void request(PlcRequest req, PlcResponse res) throws ToolControlException {
		throw rawUnsupported();
	}

	@Override
	public void completeResponse(PlcResponse res, byte[] frame) throws ToolControlException {
		throw rawUnsupported();
	}

	/** RAW 경로는 막혀 있다({@link #request}) — 객체만 만든다(커맨드가 요청 전에 만든다) */
	@Override
	public PlcRequest newPlcRequestInstance() {
		return new MelsecPlcRequest();
	}

	@Override
	public PlcResponse newPlcResponseInstance() {
		return new MelsecPlcResponse();
	}

	private static ToolControlException rawUnsupported() {
		return ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED,
			"Modbus TCP 커넥터는 POINTS 인터페이스만 받습니다 — RAW 인터페이스는 MELSEC 전용입니다");
	}
}
