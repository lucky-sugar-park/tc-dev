package com.wow.be.tc.plc.connector.modbus;

import com.wow.be.tc.plc.connector.tcp.TcpPlcAdapter;
import com.wow.be.tc.plc.protocol.modbus.ModbusDriver;

/**
 * Modbus TCP 어댑터 (docs/plc-protocol-design.md §14). 연결·재연결·시간 초과·연결 확인은 {@link TcpPlcAdapter}, 프레임은
 * {@link ModbusDriver}. 요청마다 트랜잭션 ID 가 있어 시간 초과에도 연결을 끊지 않는다.
 */
public class ModbusTcpAdapter extends TcpPlcAdapter {

	@Override
	protected String protocol() {
		return ModbusDriver.PROTOCOL;
	}
}
