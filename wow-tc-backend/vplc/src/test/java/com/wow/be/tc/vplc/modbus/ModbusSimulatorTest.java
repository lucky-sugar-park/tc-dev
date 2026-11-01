package com.wow.be.tc.vplc.modbus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.modbus.ModbusDevice.Table;

/** 가상 Modbus 장치 — PDU 처리와 TCP 왕복(단위 ID·게이트웨이 예외·안 답하기) */
class ModbusSimulatorTest {

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s.replace(" ", ""));
	}

	private static String hex(byte[] b) {
		return HexFormat.of().withUpperCase().formatHex(b);
	}

	@Test
	void 읽기_쓰기_함수() {
		ModbusDevice d = new ModbusDevice("t", 1, 1, 100);
		// FC16 HR0..1 = 0x0064, 0xFFFF → 응답은 주소·개수
		assertThat(hex(ModbusPdu.handle(d, hex("10 0000 0002 04 0064 FFFF")))).isEqualTo("1000000002");
		assertThat(hex(ModbusPdu.handle(d, hex("03 0000 0002")))).isEqualTo("03040064FFFF");
		// FC6·FC5 는 요청을 그대로 돌려준다
		assertThat(hex(ModbusPdu.handle(d, hex("06 0005 1234")))).isEqualTo("0600051234");
		assertThat(hex(ModbusPdu.handle(d, hex("05 0003 FF00")))).isEqualTo("050003FF00");
		// FC15 코일 0..2 = 1,0,1 → FC1 로 읽으면 0x05(낮은 비트부터) + 코일 3
		assertThat(hex(ModbusPdu.handle(d, hex("0F 0000 0003 01 05")))).isEqualTo("0F00000003");
		assertThat(hex(ModbusPdu.handle(d, hex("01 0000 0004")))).isEqualTo("01010D");
		// 입력 영역은 REST(장치)로만 바뀐다
		d.setRegisters(Table.INPUT_REGISTER, 9, new short[] { 7 });
		d.setBits(Table.DISCRETE_INPUT, 4, new boolean[] { true });
		assertThat(hex(ModbusPdu.handle(d, hex("04 0009 0001")))).isEqualTo("04020007");
		assertThat(hex(ModbusPdu.handle(d, hex("02 0004 0001")))).isEqualTo("020101");
	}

	@Test
	void 예외_응답() {
		ModbusDevice d = new ModbusDevice("t", 1, 1, 100);
		assertThat(hex(ModbusPdu.handle(d, hex("07")))).isEqualTo("8701");                 // 모르는 함수
		assertThat(hex(ModbusPdu.handle(d, hex("03 0063 0002")))).isEqualTo("8302");       // 범위 밖
		assertThat(hex(ModbusPdu.handle(d, hex("03 0000 007E")))).isEqualTo("8303");       // 126 레지스터 > 125
		assertThat(hex(ModbusPdu.handle(d, hex("05 0000 1234")))).isEqualTo("8503");       // 코일 값은 FF00·0000
	}

	@Test
	void TCP_왕복과_단위_ID() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		ModbusDevice one = new ModbusDevice("one", port, 1, 100);
		one.setRegisters(Table.HOLDING_REGISTER, 0, new short[] { 0x0102 });
		ModbusDevice two = new ModbusDevice("two", port, 2, 100);
		two.setRegisters(Table.HOLDING_REGISTER, 0, new short[] { 0x0203 });
		try (ModbusTcpServer server = new ModbusTcpServer("127.0.0.1", port, ModbusTcpServer.lookup(Map.of(1, one, 2, two)));
				Socket c = new Socket("127.0.0.1", port)) {
			c.setSoTimeout(2000);
			OutputStream out = c.getOutputStream();
			DataInputStream in = new DataInputStream(c.getInputStream());
			out.write(hex("0001 0000 0006 01 03 0000 0001"));
			assertThat(hex(in.readNBytes(11))).isEqualTo("000100000005010302" + "0102");
			out.write(hex("0002 0000 0006 02 03 0000 0001"));
			assertThat(hex(in.readNBytes(11))).isEqualTo("000200000005020302" + "0203");
			// 없는 단위 ID → 게이트웨이 예외 0x0B
			out.write(hex("0003 0000 0006 09 03 0000 0001"));
			assertThat(hex(in.readNBytes(9))).isEqualTo("000300000003098" + "30B");
			// 안 답하기
			one.silent(true);
			out.write(hex("0004 0000 0006 01 03 0000 0001"));
			boolean timedOut = false;
			try {
				in.readByte();
			} catch (SocketTimeoutException e) {
				timedOut = true;
			}
			assertThat(timedOut).isTrue();
			assertThat(server.connections()).isEqualTo(1);
		}
	}
}
