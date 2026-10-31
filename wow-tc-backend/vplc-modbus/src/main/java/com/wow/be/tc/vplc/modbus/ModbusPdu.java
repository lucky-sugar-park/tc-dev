package com.wow.be.tc.vplc.modbus;

import com.wow.be.tc.vplc.modbus.ModbusDevice.Table;

/**
 * Modbus PDU 처리 — 함수 코드 1·2·3·4(읽기), 5·6·15·16(쓰기). 응답 PDU 를 돌려준다(MBAP 머리는 서버가 붙인다).
 *
 * <p>예외 응답은 실제 장치처럼 — 모르는 함수 01, 주소 범위 밖 02, 개수·값이 틀림 03. 레지스터는 빅엔디안, 비트는 바이트 안에서 낮은 비트부터.
 */
public final class ModbusPdu {

	public static final int ILLEGAL_FUNCTION = 0x01;

	public static final int ILLEGAL_ADDRESS = 0x02;

	public static final int ILLEGAL_VALUE = 0x03;

	/** 게이트웨이 뒤 장치가 답하지 않음 — 그 단위 ID 의 장치가 없을 때 */
	public static final int GATEWAY_TARGET_FAILED = 0x0B;

	static final int MAX_READ_BITS = 2000;

	static final int MAX_READ_REGISTERS = 125;

	static final int MAX_WRITE_BITS = 1968;

	static final int MAX_WRITE_REGISTERS = 123;

	private ModbusPdu() {
	}

	public static byte[] handle(ModbusDevice d, byte[] pdu) {
		if (pdu.length < 1) {
			return exception(0, ILLEGAL_FUNCTION);
		}
		int fc = pdu[0] & 0xFF;
		try {
			return switch (fc) {
				case 1 -> readBits(d, Table.COIL, pdu);
				case 2 -> readBits(d, Table.DISCRETE_INPUT, pdu);
				case 3 -> readRegisters(d, Table.HOLDING_REGISTER, pdu);
				case 4 -> readRegisters(d, Table.INPUT_REGISTER, pdu);
				case 5 -> writeCoil(d, pdu);
				case 6 -> writeRegister(d, pdu);
				case 15 -> writeCoils(d, pdu);
				case 16 -> writeRegisters(d, pdu);
				default -> exception(fc, ILLEGAL_FUNCTION);
			};
		} catch (ArrayIndexOutOfBoundsException e) {
			// PDU 가 짧다
			return exception(fc, ILLEGAL_VALUE);
		}
	}

	public static byte[] exception(int fc, int code) {
		return new byte[] { (byte) (fc | 0x80), (byte) code };
	}

	private static int u16(byte[] b, int at) {
		return (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF);
	}

	private static byte[] readBits(ModbusDevice d, Table t, byte[] pdu) {
		int start = u16(pdu, 1);
		int count = u16(pdu, 3);
		if (count < 1 || count > MAX_READ_BITS) {
			return exception(pdu[0], ILLEGAL_VALUE);
		}
		if (!d.inRange(start, count)) {
			return exception(pdu[0], ILLEGAL_ADDRESS);
		}
		boolean[] bits = d.bits(t, start, count);
		int bytes = (count + 7) / 8;
		byte[] out = new byte[2 + bytes];
		out[0] = pdu[0];
		out[1] = (byte) bytes;
		for (int i = 0; i < count; i++) {
			if (bits[i]) {
				out[2 + i / 8] |= (byte) (1 << (i % 8));
			}
		}
		return out;
	}

	private static byte[] readRegisters(ModbusDevice d, Table t, byte[] pdu) {
		int start = u16(pdu, 1);
		int count = u16(pdu, 3);
		if (count < 1 || count > MAX_READ_REGISTERS) {
			return exception(pdu[0], ILLEGAL_VALUE);
		}
		if (!d.inRange(start, count)) {
			return exception(pdu[0], ILLEGAL_ADDRESS);
		}
		short[] regs = d.registers(t, start, count);
		byte[] out = new byte[2 + count * 2];
		out[0] = pdu[0];
		out[1] = (byte) (count * 2);
		for (int i = 0; i < count; i++) {
			out[2 + i * 2] = (byte) (regs[i] >> 8);
			out[3 + i * 2] = (byte) regs[i];
		}
		return out;
	}

	private static byte[] writeCoil(ModbusDevice d, byte[] pdu) {
		int addr = u16(pdu, 1);
		int value = u16(pdu, 3);
		if (value != 0xFF00 && value != 0x0000) {
			return exception(pdu[0], ILLEGAL_VALUE);
		}
		if (!d.inRange(addr, 1)) {
			return exception(pdu[0], ILLEGAL_ADDRESS);
		}
		d.setBits(Table.COIL, addr, new boolean[] { value == 0xFF00 });
		return java.util.Arrays.copyOf(pdu, 5);
	}

	private static byte[] writeRegister(ModbusDevice d, byte[] pdu) {
		int addr = u16(pdu, 1);
		if (!d.inRange(addr, 1)) {
			return exception(pdu[0], ILLEGAL_ADDRESS);
		}
		d.setRegisters(Table.HOLDING_REGISTER, addr, new short[] { (short) u16(pdu, 3) });
		return java.util.Arrays.copyOf(pdu, 5);
	}

	private static byte[] writeCoils(ModbusDevice d, byte[] pdu) {
		int start = u16(pdu, 1);
		int count = u16(pdu, 3);
		int bytes = pdu[5] & 0xFF;
		if (count < 1 || count > MAX_WRITE_BITS || bytes != (count + 7) / 8 || pdu.length < 6 + bytes) {
			return exception(pdu[0], ILLEGAL_VALUE);
		}
		if (!d.inRange(start, count)) {
			return exception(pdu[0], ILLEGAL_ADDRESS);
		}
		boolean[] bits = new boolean[count];
		for (int i = 0; i < count; i++) {
			bits[i] = ((pdu[6 + i / 8] >> (i % 8)) & 1) == 1;
		}
		d.setBits(Table.COIL, start, bits);
		return java.util.Arrays.copyOf(pdu, 5);
	}

	private static byte[] writeRegisters(ModbusDevice d, byte[] pdu) {
		int start = u16(pdu, 1);
		int count = u16(pdu, 3);
		int bytes = pdu[5] & 0xFF;
		if (count < 1 || count > MAX_WRITE_REGISTERS || bytes != count * 2 || pdu.length < 6 + bytes) {
			return exception(pdu[0], ILLEGAL_VALUE);
		}
		if (!d.inRange(start, count)) {
			return exception(pdu[0], ILLEGAL_ADDRESS);
		}
		short[] regs = new short[count];
		for (int i = 0; i < count; i++) {
			regs[i] = (short) u16(pdu, 6 + i * 2);
		}
		d.setRegisters(Table.HOLDING_REGISTER, start, regs);
		return java.util.Arrays.copyOf(pdu, 5);
	}
}
