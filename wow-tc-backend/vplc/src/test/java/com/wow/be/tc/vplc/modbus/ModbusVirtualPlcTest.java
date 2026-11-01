package com.wow.be.tc.vplc.modbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;

import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.modbus.ModbusDevice.Table;

/** 가상 Modbus 장치(vplc 안) — 한 포트에 단위 ID 둘, 일시정지, 마지막 장치 정지 때 포트 닫기, 설정 검사 */
class ModbusVirtualPlcTest {

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s.replace(" ", ""));
	}

	private static String hex(byte[] b) {
		return HexFormat.of().withUpperCase().formatHex(b);
	}

	private static VirtualPlcInfo info(String name, int port, int unit) throws VirtualPlcException {
		VirtualPlcInfo i = new VirtualPlcInfo();
		i.setId(name);
		i.setName(name);
		i.setProtocol(Protocol.MODBUS_TCP);
		i.setIpAddress("127.0.0.1");
		i.setStartPort(port);
		i.setSettings(new java.util.LinkedHashMap<>(Map.of("unitId", unit, "size", 100)));
		new ModbusProvider().normalize(i);
		return i;
	}

	@Test
	void 한_포트_두_장치_일시정지_정지() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		ModbusVirtualPlc one = new ModbusVirtualPlc(info("mb-one", port, 1));
		ModbusVirtualPlc two = new ModbusVirtualPlc(info("mb-two", port, 2));
		one.memory().setRegisters(Table.HOLDING_REGISTER, 0, new short[] { 0x0102 });
		two.memory().setRegisters(Table.HOLDING_REGISTER, 0, new short[] { 0x0203 });
		one.start();
		two.start();
		try (Socket c = new Socket("127.0.0.1", port)) {
			c.setSoTimeout(1500);
			OutputStream out = c.getOutputStream();
			DataInputStream in = new DataInputStream(c.getInputStream());
			out.write(hex("0001 0000 0006 01 03 0000 0001"));
			assertThat(hex(in.readNBytes(11))).isEqualTo("0001000000050103020102");
			out.write(hex("0002 0000 0006 02 03 0000 0001"));
			assertThat(hex(in.readNBytes(11))).isEqualTo("0002000000050203020203");
			assertThat(one.connections()).isEqualTo(1);

			// 일시정지 — 단위 1 은 답하지 않고, 단위 2 는 그대로
			one.pause();
			assertThat(one.getStatus()).isEqualTo(PlcStatus.PAUSED);
			out.write(hex("0003 0000 0006 02 03 0000 0001"));
			assertThat(hex(in.readNBytes(11))).isEqualTo("0003000000050203020203");
			out.write(hex("0004 0000 0006 01 03 0000 0001"));
			assertThatThrownBy(in::readByte).isInstanceOf(SocketTimeoutException.class);
			one.resume();

			// 같은 포트·단위 ID 는 하나만
			ModbusVirtualPlc dup = new ModbusVirtualPlc(info("mb-dup", port, 1));
			assertThatThrownBy(dup::start).hasMessageContaining("이미 실행 중");

			// 하나를 멈춰도 포트는 열려 있고, 멈춘 단위 ID 는 게이트웨이 예외
			one.stop();
			assertThat(one.getStatus()).isEqualTo(PlcStatus.STOPPED);
		}
		try (Socket c = new Socket("127.0.0.1", port)) {
			c.setSoTimeout(1500);
			c.getOutputStream().write(hex("0005 0000 0006 01 03 0000 0001"));
			assertThat(hex(new DataInputStream(c.getInputStream()).readNBytes(9))).isEqualTo("00050000000301830B");
		}
		// 마지막 장치가 멈추면 포트를 닫는다
		two.stop();
		assertThatThrownBy(() -> new Socket("127.0.0.1", port).close()).isInstanceOf(ConnectException.class);
	}

	@Test
	void 상태_규칙과_영역_메모리() throws Exception {
		int port;
		try (ServerSocket s = new ServerSocket(0)) {
			port = s.getLocalPort();
		}
		ModbusVirtualPlc d = new ModbusVirtualPlc(info("mb-state", port, 1));
		assertThat(d.getStatus()).isEqualTo(PlcStatus.PUBLISHED);
		// 실행 중이 아니면 일시정지·재개는 409, 정지는 상태만
		assertThatThrownBy(d::pause).hasMessageContaining("일시정지할 수 없는");
		assertThatThrownBy(d::resume).hasMessageContaining("재개할 수 없는");
		d.stop();
		assertThat(d.getStatus()).isEqualTo(PlcStatus.STOPPED);
		d.start();
		assertThatThrownBy(d::start).hasMessageContaining("시작할 수 없는");
		d.pause();
		assertThatThrownBy(d::pause).hasMessageContaining("일시정지할 수 없는");
		d.resume();

		// 영역 — HR·IR 워드, CO·DI 비트, 번호는 0부터
		assertThat(d.areas()).extracting(com.wow.be.tc.vplc.core.memory.AreaMemory.Area::label).containsExactly("HR", "IR", "CO", "DI");
		d.write("hr", 3, new int[] { 1, 0xFFFF });
		assertThat(d.read("hr", 3, 2)).containsExactly(1, 0xFFFF);
		d.write("co", 0, new int[] { 1, 0, 1 });
		assertThat(d.read("co", 0, 3)).containsExactly(1, 0, 1);
		d.clear("co");
		assertThat(d.read("co", 0, 3)).containsExactly(0, 0, 0);

		d.release();
		assertThat(d.getStatus()).isEqualTo(PlcStatus.RELEASED);
		assertThatThrownBy(() -> new Socket("127.0.0.1", port).close()).isInstanceOf(ConnectException.class);
	}

	@Test
	void 설정_검사() {
		assertThatThrownBy(() -> info("x", 15020, 300)).hasMessageContaining("단위 ID");
		assertThatThrownBy(() -> info("x", 0, 1)).hasMessageContaining("포트");
	}
}
