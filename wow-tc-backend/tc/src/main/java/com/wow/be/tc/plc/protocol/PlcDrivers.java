package com.wow.be.tc.plc.protocol;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wow.be.tc.plc.protocol.melsec.MelsecDriver;
import com.wow.be.tc.plc.protocol.modbus.ModbusDriver;

/**
 * 프로토콜 → 드라이버. 드라이버는 상태가 없어 하나씩만 둔다. 어댑터가 리플렉션으로 만들어져 Spring 빈을 받지 못하므로 정적 표로 둔다.
 */
public final class PlcDrivers {

	public static final String DEFAULT_PROTOCOL = MelsecDriver.PROTOCOL;

	private static final Map<String, PlcDriver> DRIVERS = List.<PlcDriver>of(new MelsecDriver(), new ModbusDriver()).stream()
		.collect(Collectors.toUnmodifiableMap(PlcDriver::protocol, Function.identity()));

	private PlcDrivers() {
	}

	/** @throws IllegalArgumentException 모르는 프로토콜(400) */
	public static PlcDriver of(String protocol) {
		PlcDriver driver = DRIVERS.get(protocol == null || protocol.isBlank() ? DEFAULT_PROTOCOL : protocol);
		if (driver == null) {
			throw new IllegalArgumentException("지원하지 않는 PLC 프로토콜입니다: " + protocol + " (지원: " + DRIVERS.keySet() + ")");
		}
		return driver;
	}
}
