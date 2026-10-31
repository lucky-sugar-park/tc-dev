package com.wow.be.tc.plc.protocol.modbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlannedRequest;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.WireProtocol;
import com.wow.be.tc.plc.protocol.modbus.ModbusAddress.Table;

/** Modbus TCP 드라이버 — 주소, 설정, 묶기, 프레임 바이트, 예외 응답, 전송 규약 (docs/plc-protocol-design.md §6.2·§14) */
class ModbusDriverTest {

	private final ModbusDriver driver = new ModbusDriver();

	private final Map<String, Object> defaults = driver.normalizeSettings(Map.of(), null);

	private static PointInfo point(String name, String address, PointType type) {
		return point(name, address, type, 1, 0);
	}

	private static PointInfo point(String name, String address, PointType type, int count, int length) {
		PointInfo p = new PointInfo();
		p.setName(name);
		p.setAddress(address);
		p.setType(type);
		p.setCount(count);
		p.setLength(length);
		return p;
	}

	private static String hex(byte[] b) {
		return HexFormat.of().withUpperCase().formatHex(b);
	}

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s.replace(" ", ""));
	}

	/** 보낸 프레임을 모으고 정해 둔 응답을 차례로 돌려준다 */
	private static final class Fake {
		final List<String> sent = new ArrayList<>();
		final List<byte[]> replies;

		Fake(byte[]... replies) {
			this.replies = new ArrayList<>(List.of(replies));
		}

		byte[] exchange(String reqId, byte[] frame) {
			sent.add(hex(frame));
			return replies.remove(0);
		}
	}

	/** 응답 프레임: MBAP(트랜잭션 0, 단위 1) + PDU */
	private static byte[] reply(String pdu) {
		byte[] p = hex(pdu);
		return hex(String.format("00000000%04X01", p.length + 1) + pdu.replace(" ", ""));
	}

	private PlcResult read(Map<String, Object> settings, Fake fake, PointInfo... points) throws ToolControlException {
		return driver.execute(new PlcOperation(ModbusDriver.PROTOCOL, settings, PlcOperation.Kind.READ, List.of(points), Map.of(), "r"),
			fake::exchange);
	}

	private PlcResult write(Fake fake, Map<String, Object> values, PointInfo... points) throws ToolControlException {
		return driver.execute(new PlcOperation(ModbusDriver.PROTOCOL, defaults, PlcOperation.Kind.WRITE, List.of(points), values, "w"),
			fake::exchange);
	}

	@Test
	void 주소() {
		assertThat(ModbusAddress.parse("HR1", 1)).isEqualTo(new ModbusAddress(Table.HOLDING_REGISTER, 0));
		assertThat(ModbusAddress.parse("hr0", 0)).isEqualTo(new ModbusAddress(Table.HOLDING_REGISTER, 0));
		assertThat(ModbusAddress.parse("IR10", 1)).isEqualTo(new ModbusAddress(Table.INPUT_REGISTER, 9));
		assertThat(ModbusAddress.parse("40001", 0)).isEqualTo(new ModbusAddress(Table.HOLDING_REGISTER, 0));   // Modicon 은 늘 1부터
		assertThat(ModbusAddress.parse("300010", 1)).isEqualTo(new ModbusAddress(Table.INPUT_REGISTER, 9));
		assertThat(ModbusAddress.parse("00001", 1)).isEqualTo(new ModbusAddress(Table.COIL, 0));
		assertThat(ModbusAddress.parse("10005", 1)).isEqualTo(new ModbusAddress(Table.DISCRETE_INPUT, 4));
		assertThat(ModbusAddress.parse("465536", 1).offset()).isEqualTo(65535);
		assertThatThrownBy(() -> ModbusAddress.parse("HR0", 1)).hasMessageContaining("범위");
		assertThatThrownBy(() -> ModbusAddress.parse("D100", 1)).hasMessageContaining("Modbus 주소는");
		assertThatThrownBy(() -> ModbusAddress.parse("20001", 1)).hasMessageContaining("Modbus 주소는");
	}

	@Test
	void 설정() {
		assertThat(defaults).containsEntry("unitId", 1).containsEntry("byteOrder", "BIG").containsEntry("wordOrder", "BIG")
			.containsEntry("addressBase", 1).containsEntry("maxRegistersPerRequest", 125).containsEntry("maxCoilsPerRequest", 2000)
			.containsEntry("healthCheckIntervalSec", 30);
		assertThat(driver.normalizeSettings(Map.of("wordOrder", "little", "unitId", "17"), "ASCII"))
			.containsEntry("wordOrder", "LITTLE").containsEntry("unitId", 17);
		assertThatThrownBy(() -> driver.normalizeSettings(Map.of("frame", "3E_BINARY"), null)).hasMessageContaining("없는 항목");
		assertThatThrownBy(() -> driver.normalizeSettings(Map.of("maxRegistersPerRequest", 200), null)).hasMessageContaining("1~125");
		assertThatThrownBy(() -> driver.normalizeSettings(Map.of("byteOrder", "MIDDLE"), null)).hasMessageContaining("BIG 또는 LITTLE");
	}

	@Test
	void 점_검증() {
		driver.validatePoint(point("a", "HR1", PointType.FLOAT32), InterfaceType.WRITE);
		driver.validatePoint(point("b", "DI1", PointType.BOOL), InterfaceType.READ);
		assertThatThrownBy(() -> driver.validatePoint(point("c", "CO1", PointType.INT16), InterfaceType.READ)).hasMessageContaining("BOOL 로만");
		assertThatThrownBy(() -> driver.validatePoint(point("d", "HR1", PointType.BOOL), InterfaceType.READ)).hasMessageContaining("BOOL 을 쓸 수 없습니다");
		assertThatThrownBy(() -> driver.validatePoint(point("e", "IR1", PointType.INT16), InterfaceType.WRITE)).hasMessageContaining("읽기만");
		assertThatThrownBy(() -> driver.validatePoint(point("f", "DI1", PointType.BOOL), InterfaceType.WRITE)).hasMessageContaining("읽기만");
		assertThatThrownBy(() -> driver.validatePoint(point("g", "HR1", PointType.STRING), InterfaceType.READ)).hasMessageContaining("length");
		assertThatThrownBy(() -> driver.validatePoint(point("h", "HR1", PointType.INT16, 124, 0), InterfaceType.WRITE)).hasMessageContaining("123");
	}

	@Test
	void 레지스터_읽기_묶기와_값() throws ToolControlException {
		// HR1(0) INT16, HR3(2..3) INT32 → 한 번(0..3). HR20(19..20) 은 빈 칸 15 라 따로
		Fake fake = new Fake(
			reply("03 08 0064 0000 0001 0002"),
			reply("03 04 3FC0 0000"));
		PlcResult r = read(defaults, fake,
			point("a", "HR1", PointType.INT16), point("b", "HR3", PointType.INT32), point("c", "HR20", PointType.FLOAT32));
		assertThat(fake.sent).containsExactly("000000000006010300000004", "000000000006010300130002");
		assertThat(r.status().ok()).isTrue();
		assertThat(r.values()).containsEntry("a", 100).containsEntry("b", 65538).containsEntry("c", 1.5f);
	}

	@Test
	void 워드_순서_LITTLE() throws ToolControlException {
		Map<String, Object> cdab = driver.normalizeSettings(Map.of("wordOrder", "LITTLE", "unitId", 2), null);
		Fake fake = new Fake(hex("000000000007020304 0002 0001".replace(" ", "")));
		PlcResult r = read(cdab, fake, point("v", "40001", PointType.INT32));
		assertThat(fake.sent).containsExactly("000000000006020300000002");
		assertThat(r.values()).containsEntry("v", 65538);
	}

	@Test
	void 입력_레지스터와_문자열() throws ToolControlException {
		Fake fake = new Fake(reply("04 04 4142 4300"));
		PlcResult r = read(defaults, fake, point("s", "IR1", PointType.STRING, 1, 4));
		assertThat(fake.sent).containsExactly("000000000006010400000002");
		assertThat(r.values()).containsEntry("s", "ABC");
	}

	@Test
	void 코일_읽기() throws ToolControlException {
		// CO1..CO3 + CO5 → 한 번(0..4, 5개). 0x15 = 1 0 1 0 1 (낮은 비트가 첫 코일)
		Fake fake = new Fake(reply("01 01 15"));
		PlcResult r = read(defaults, fake, point("bits", "CO1", PointType.BOOL, 3, 0), point("one", "CO5", PointType.BOOL));
		assertThat(fake.sent).containsExactly("000000000006010100000005");
		assertThat(r.values().get("bits")).isEqualTo(List.of(true, false, true));
		assertThat(r.values()).containsEntry("one", true);
	}

	@Test
	void 예외_응답() throws ToolControlException {
		Fake fake = new Fake(reply("83 02"));
		PlcResult r = read(defaults, fake, point("a", "HR100", PointType.INT16));
		assertThat(r.status().ok()).isFalse();
		assertThat(r.status().code()).isEqualTo(2);
		assertThat(r.status().protocol()).isEqualTo("MODBUS_TCP");
		assertThat(r.status().text()).contains("0x02").contains("잘못된 데이터 주소");
	}

	@Test
	void 응답_검증() {
		assertThatThrownBy(() -> read(defaults, new Fake(reply("04 02 0001")), point("a", "HR1", PointType.INT16)))
			.hasMessageContaining("함수 코드");
		assertThatThrownBy(() -> read(defaults, new Fake(reply("03 04 0001 0002")), point("a", "HR1", PointType.INT16)))
			.hasMessageContaining("바이트여야");
		assertThatThrownBy(() -> read(defaults, new Fake(hex("000000000009010302000A")), point("a", "HR1", PointType.INT16)))
			.hasMessageContaining("길이 칸");
	}

	@Test
	void 쓰기_레지스터() throws ToolControlException {
		Map<String, Object> values = new LinkedHashMap<>();
		values.put("a", 7);
		values.put("b", 8);
		values.put("c", 0x1234);
		Fake fake = new Fake(reply("10 0000 0002"), reply("06 0009 1234"));
		PlcResult r = write(fake, values, point("a", "HR1", PointType.INT16), point("b", "HR2", PointType.INT16),
			point("c", "HR10", PointType.UINT16));
		assertThat(fake.sent).containsExactly("00000000000B01100000000204 0007 0008".replace(" ", ""), "000000000006010600091234");
		assertThat(r.status().ok()).isTrue();
	}

	@Test
	void 쓰기_코일() throws ToolControlException {
		Fake fake = new Fake(reply("0F 0000 0003"), reply("05 0009 FF00"));
		PlcResult r = write(fake, Map.of("bits", List.of(true, false, true), "one", "on"),
			point("bits", "CO1", PointType.BOOL, 3, 0), point("one", "CO10", PointType.BOOL));
		assertThat(fake.sent).containsExactly("000000000008010F000000030105", "00000000000601050009FF00");
		assertThat(r.status().ok()).isTrue();
	}

	@Test
	void 쓰기_오류() {
		assertThatThrownBy(() -> write(new Fake(), Map.of(), point("a", "HR1", PointType.INT16))).hasMessageContaining("쓰기 값이 없습니다");
		assertThatThrownBy(() -> write(new Fake(), Map.of("a", 70000), point("a", "HR1", PointType.INT16))).hasMessageContaining("범위");
		assertThatThrownBy(() -> write(new Fake(), Map.of("a", 1), point("a", "IR1", PointType.INT16)))
			.isInstanceOf(ToolControlException.class).hasMessageContaining("읽기만");
		assertThatThrownBy(() -> write(new Fake(reply("10 0001 0002")), Map.of("a", 1, "b", 2),
			point("a", "HR1", PointType.INT16), point("b", "HR2", PointType.INT16))).hasMessageContaining("맞지 않습니다");
	}

	@Test
	void 전송_규약() {
		WireProtocol wire = driver.wire(defaults);
		byte[] frame = reply("03 02 0064");
		assertThat(wire.responseLength(frame, 5)).isZero();
		assertThat(wire.responseLength(frame, frame.length - 1)).isZero();
		assertThat(wire.responseLength(frame, frame.length)).isEqualTo(frame.length);
		byte[] two = new byte[frame.length * 2];
		System.arraycopy(frame, 0, two, 0, frame.length);
		System.arraycopy(frame, 0, two, frame.length, frame.length);
		assertThat(wire.responseLength(two, two.length)).isEqualTo(frame.length);
		assertThat(wire.responseLength(hex("0000 0001 0006 01"), 7)).isEqualTo(-1);   // 프로토콜 ID 가 0 이 아님
		byte[] req = wire.healthCheckRequest();
		assertThat(hex(req)).isEqualTo("000000000006010300000001");
		assertThat(wire.hasSerial(req)).isTrue();
		wire.setSerial(req, 0xABCD);
		assertThat(wire.serialOf(req)).isEqualTo(0xABCD);
		assertThat(wire.healthCheckOk(reply("03 02 0000"))).isTrue();
		assertThat(wire.healthCheckOk(reply("83 02"))).isTrue();   // 주소가 없어도 답하면 살아 있다
		assertThat(wire.healthCheckOk(hex("0000000000020101"))).isFalse();
	}

	@Test
	void 미리보기는_실행이_보내는_프레임과_같다() throws ToolControlException {
		List<PointInfo> reads = List.of(point("bits", "CO1", PointType.BOOL, 3, 0), point("one", "CO5", PointType.BOOL), point("v", "HR1", PointType.INT32));
		PlcOperation read = new PlcOperation(ModbusDriver.PROTOCOL, defaults, PlcOperation.Kind.READ, reads, Map.of(), "r");
		Fake fake = new Fake(reply("01 01 15"), reply("03 04 0001 0002"));
		driver.execute(read, fake::exchange);
		List<PlannedRequest> plan = driver.plan(read);
		assertThat(plan).extracting(PlannedRequest::frameHex).containsExactlyElementsOf(fake.sent);
		assertThat(plan).extracting(PlannedRequest::target).containsExactly("CO0", "HR0");
		assertThat(plan).extracting(PlannedRequest::function).containsExactly("FC1 Read Coils", "FC3 Read Holding Registers");

		List<PointInfo> writes = List.of(point("a", "HR10", PointType.INT16), point("b", "HR11", PointType.INT16), point("c", "CO1", PointType.BOOL));
		PlcOperation write = new PlcOperation(ModbusDriver.PROTOCOL, defaults, PlcOperation.Kind.WRITE, writes, Map.of("a", 1, "b", 2, "c", true), "w");
		Fake wfake = new Fake(reply("05 0000 FF00"), reply("10 0009 0002"));
		driver.execute(write, wfake::exchange);
		List<PlannedRequest> wplan = driver.plan(write);
		assertThat(wplan).extracting(PlannedRequest::frameHex).containsExactlyElementsOf(wfake.sent);
		assertThat(wplan).extracting(PlannedRequest::function).containsExactly("FC5 Write Single Coil", "FC16 Write Multiple Registers");
		assertThat(wplan.get(1).points()).containsExactly("a", "b");
	}

	@Test
	void 인터페이스_설정_unitId_와_쓰기_함수() {
		assertThat(driver.normalizeInterfaceSettings(Map.of("unitId", "5", "writeFunction", "multiple", "wordOrder", "little")))
			.containsEntry("unitId", 5).containsEntry("writeFunction", "MULTIPLE").containsEntry("wordOrder", "LITTLE");
		assertThatThrownBy(() -> driver.normalizeInterfaceSettings(Map.of("addressBase", 0))).hasMessageContaining("없는 항목");
		assertThatThrownBy(() -> driver.normalizeInterfaceSettings(Map.of("writeFunction", "BOTH"))).hasMessageContaining("AUTO");

		List<PointInfo> one = List.of(point("a", "HR10", PointType.INT16));
		// unitId 5, 레지스터 하나를 FC16 으로
		List<PlannedRequest> multiple = driver.plan(new PlcOperation(ModbusDriver.PROTOCOL,
			driver.effectiveSettings(defaults, Map.of("unitId", 5, "writeFunction", "MULTIPLE")), PlcOperation.Kind.WRITE, one, Map.of("a", 7), "w"));
		assertThat(multiple.get(0).function()).isEqualTo("FC16 Write Multiple Registers");
		assertThat(multiple.get(0).frameHex()).isEqualTo("000000000009" + "05" + "10" + "0009" + "0001" + "02" + "0007");
		// 이어진 레지스터 둘을 FC6 두 번으로
		List<PointInfo> two = List.of(point("a", "HR10", PointType.INT16), point("b", "HR11", PointType.INT16));
		List<PlannedRequest> single = driver.plan(new PlcOperation(ModbusDriver.PROTOCOL,
			driver.effectiveSettings(defaults, Map.of("writeFunction", "SINGLE")), PlcOperation.Kind.WRITE, two, Map.of("a", 1, "b", 2), "w"));
		assertThat(single).extracting(PlannedRequest::function).containsExactly("FC6 Write Single Register", "FC6 Write Single Register");
		assertThat(single).extracting(PlannedRequest::target).containsExactly("HR9", "HR10");
	}

	@Test
	void SINGLE_쓰기는_실행도_칸마다_FC6() throws ToolControlException {
		Map<String, Object> s = driver.effectiveSettings(defaults, Map.of("writeFunction", "SINGLE"));
		Fake fake = new Fake(reply("06 0009 0001"), reply("06 000A 0002"));
		PlcResult r = driver.execute(new PlcOperation(ModbusDriver.PROTOCOL, s, PlcOperation.Kind.WRITE,
			List.of(point("a", "HR10", PointType.INT16), point("b", "HR11", PointType.INT16)), Map.of("a", 1, "b", 2), "w"), fake::exchange);
		assertThat(r.status().ok()).isTrue();
		assertThat(fake.sent).containsExactly("000000000006010600090001", "000000000006010600 0A0002".replace(" ", ""));
	}
}
