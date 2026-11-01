package com.wow.be.tc.vplc;

public class Types {

	public enum PlcStatus {
		REGISTERED, PUBLISHED, RUNNING, PAUSED, STOPPED, RELEASED, UNKNOWN
	}
	
	/**
	 * 가상 PLC 종류 — tc 커넥터 프로토콜과 같은 이름. 한 vplc 앱이 세 종류를 여러 대씩 띄운다(docs/virtual-devices.md).
	 * 예전 줄(PROTOCOL 이 비어 있음)은 MELSEC_MC.
	 */
	public enum Protocol {
		MELSEC_MC, MODBUS_TCP, OPC_UA;

		public static Protocol of(String s) {
			return s == null || s.isBlank() ? MELSEC_MC : Protocol.valueOf(s.trim().toUpperCase());
		}
	}
}
