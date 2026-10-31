package com.wow.be.tc.plc.protocol;

/**
 * 바이트·워드 순서. 바이트 순서는 16비트 워드 안의 두 바이트, 워드 순서는 32·64비트 값을 이루는 워드들의 순서
 * (LITTLE = 하위 워드 먼저). MELSEC 은 둘 다 LITTLE, Modbus 는 보통 둘 다 BIG 이지만 장비마다 워드 순서가 다르다.
 */
public enum ByteOrder {
	BIG, LITTLE
}
