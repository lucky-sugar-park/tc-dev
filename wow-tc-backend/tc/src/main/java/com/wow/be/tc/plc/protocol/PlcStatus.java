package com.wow.be.tc.plc.protocol;

/**
 * PLC 가 돌려준 상태 (docs/plc-protocol-design.md §4.3) — MELSEC 종료 코드, Modbus 예외 코드, OPC UA StatusCode.
 * {@code code} 0 이 정상이다.
 */
public record PlcStatus(String protocol, long code, String text) {

	public boolean ok() {
		return code == 0;
	}

	public static PlcStatus ok(String protocol) {
		return new PlcStatus(protocol, 0, "OK");
	}
}
