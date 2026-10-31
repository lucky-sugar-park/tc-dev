package com.wow.be.tc.plc.protocol.melsec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PointType;

/** MELSEC 3E 드라이버 — 주소, 묶기, 프레임 바이트, 응답 검증 (docs/plc-protocol-design.md §6.1) */
class MelsecDriverTest {

	private final MelsecDriver driver = new MelsecDriver();

	private final Map<String, Object> binary = driver.normalizeSettings(Map.of(), null);

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

	/** BINARY 정상 응답: D0 00 00 FF FF 03 00 + 길이 + 종료 코드 0000 + 데이터 */
	private static byte[] ok(String data) {
		byte[] d = hex(data);
		int len = d.length + 2;
		return hex(String.format("D00000FFFF0300%02X%02X0000", len & 0xFF, len >> 8) + data.replace(" ", ""));
	}

	@Test
	void 주소() {
		assertThat(MelsecAddress.parse("D100")).isEqualTo(new MelsecAddress(MelsecDevice.D, 100));
		assertThat(MelsecAddress.parse("x1f")).isEqualTo(new MelsecAddress(MelsecDevice.X, 0x1F));
		assertThat(MelsecAddress.parse("SD10")).isEqualTo(new MelsecAddress(MelsecDevice.SD, 10));
		assertThat(MelsecAddress.parse("ZR20000")).isEqualTo(new MelsecAddress(MelsecDevice.ZR, 20000));
		assertThat(MelsecAddress.parse("DX10").device()).isEqualTo(MelsecDevice.DX);
		assertThatThrownBy(() -> MelsecAddress.parse("Q100")).hasMessageContaining("읽을 수 없습니다");
		assertThatThrownBy(() -> MelsecAddress.parse("D1A")).hasMessageContaining("읽을 수 없습니다");
	}

	@Test
	void 점_검증() {
		driver.validatePoint(point("d100", "D100", PointType.INT16), InterfaceType.READ);
		driver.validatePoint(point("m", "M10", PointType.BOOL, 3, 0), InterfaceType.READ);
		assertThatThrownBy(() -> driver.validatePoint(point("b", "D100", PointType.BOOL), InterfaceType.READ))
			.hasMessageContaining("워드 디바이스");
		assertThatThrownBy(() -> driver.validatePoint(point("m", "M10", PointType.INT16), InterfaceType.READ))
			.hasMessageContaining("BOOL");
		assertThatThrownBy(() -> driver.validatePoint(point("s", "D0", PointType.STRING), InterfaceType.READ))
			.hasMessageContaining("length");
		assertThatThrownBy(() -> driver.validatePoint(point("9x", "D0", PointType.INT16), InterfaceType.READ))
			.hasMessageContaining("이름");
	}

	@Test
	void 설정_기본값과_옛_프레임_형식() {
		assertThat(binary).containsEntry("frame", "3E_BINARY").containsEntry("pcNo", 0xFF).containsEntry("ioNo", 0x03FF);
		assertThat(driver.normalizeSettings(null, "ASCII")).containsEntry("frame", "3E_ASCII");
		assertThat(driver.normalizeSettings(Map.of("ioNo", "0x03E0"), null)).containsEntry("ioNo", 0x03E0);
		assertThatThrownBy(() -> driver.normalizeSettings(Map.of("frame", "4E_BINARY"), null)).hasMessageContaining("4E");
		assertThatThrownBy(() -> driver.normalizeSettings(Map.of("unknown", 1), null)).hasMessageContaining("없는 항목");
	}

	@Test
	void 연속_워드_읽기는_한_프레임_RAW_정의와_같은_바이트() throws ToolControlException {
		Fake fake = new Fake(ok("4D00 0100"));
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.READ,
			List.of(point("d100", "D100", PointType.INT16), point("d101", "D101", PointType.INT16)), Map.of(), "t"), fake::exchange);
		// e2e fixture-if-read.json(D100 2워드)과 같은 요청 — 서브커맨드만 0000(워드 단위)
		assertThat(fake.sent).containsExactly("500000FFFF03000C00100001040000640000A80200");
		assertThat(r.status().ok()).isTrue();
		assertThat(r.values()).containsEntry("d100", 77).containsEntry("d101", 1);
	}

	@Test
	void 가까운_점은_묶고_먼_점이나_다른_디바이스는_나눈다() throws ToolControlException {
		Fake fake = new Fake(ok("0100 0000 0000 0200"), ok("0900"), ok("0500"));
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.READ,
			List.of(point("a", "D0", PointType.INT16), point("b", "D3", PointType.INT16),
				point("c", "D500", PointType.INT16), point("w", "W10", PointType.INT16)), Map.of(), "t"), fake::exchange);
		assertThat(fake.sent).hasSize(3);
		assertThat(fake.sent.get(0)).endsWith("000000A80400");        // D0 부터 4워드
		assertThat(fake.sent.get(1)).endsWith("F40100A80100");        // D500 1워드
		assertThat(fake.sent.get(2)).endsWith("100000B40100");        // W10(16진) 1워드
		assertThat(r.values()).containsEntry("a", 1).containsEntry("b", 2).containsEntry("c", 9).containsEntry("w", 5);
	}

	@Test
	void 비트_디바이스는_워드로_읽어_비트를_꺼낸다() throws ToolControlException {
		// M10~M12 → M0 부터 1워드(비트 번호 0 = 시작 주소 0). 워드 값 0x1400 = 비트 10, 12 켜짐
		Fake fake = new Fake(ok("0014"));
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.READ,
			List.of(point("m", "M10", PointType.BOOL, 3, 0)), Map.of(), "t"), fake::exchange);
		assertThat(fake.sent.get(0)).endsWith("000000900100");
		assertThat(r.values().get("m")).isEqualTo(List.of(true, false, true));
	}

	@Test
	void 쓰기_이어진_워드는_한_프레임_비트는_비트_단위() throws ToolControlException {
		Fake fake = new Fake(ok(""), ok(""));
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.WRITE,
			List.of(point("a", "D200", PointType.INT16), point("b", "D201", PointType.INT32), point("m", "M5", PointType.BOOL, 3, 0)),
			Map.of("a", 1, "b", 0x12345678, "m", List.of(true, false, true)), "t"), fake::exchange);
		assertThat(r.status().ok()).isTrue();
		// D200~D202 3워드 일괄 쓰기 1401/0000: 길이 12+6=18(0x12)
		assertThat(fake.sent).contains("500000FFFF03001200100001140000C80000A80300" + "0100" + "78563412");
		// M5~M7 비트 단위 쓰기 1401/0001, 3점 → 2바이트(10 10)
		assertThat(fake.sent).contains("500000FFFF03000E00100001140100050000900300" + "1010");
	}

	@Test
	void 쓰기_값이_없거나_잘못되면_400() {
		assertThatThrownBy(() -> driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.WRITE,
			List.of(point("a", "D200", PointType.INT16)), Map.of(), "t"), (id, f) -> ok("")))
			.isInstanceOf(ToolControlException.class).hasMessageContaining("쓰기 값이 없습니다")
			.extracting(e -> ((ToolControlException) e).getErrorCode()).isEqualTo(ToolControlException.INVALID_REQUEST);
		assertThatThrownBy(() -> driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.WRITE,
			List.of(point("a", "D200", PointType.INT16)), Map.of("a", 99999), "t"), (id, f) -> ok("")))
			.hasMessageContaining("범위");
	}

	@Test
	void 종료_코드는_상태로_응답_길이가_다르면_오류() throws ToolControlException {
		byte[] err = hex("D00000FFFF03000B00 59C0 00FFFF0300 0104 0000");  // 종료 코드 C059 + 오류 정보 9바이트
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.READ,
			List.of(point("a", "D0", PointType.INT16)), Map.of(), "t"), (id, f) -> err);
		assertThat(r.status().code()).isEqualTo(0xC059);
		assertThat(r.status().text()).contains("C059");

		byte[] shortLen = hex("D00000FFFF03000600 0000 4D00");  // 길이 칸 6 인데 실제 4
		assertThatThrownBy(() -> driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.READ,
			List.of(point("a", "D0", PointType.INT16)), Map.of(), "t"), (id, f) -> shortLen)).hasMessageContaining("길이");

		byte[] fewer = ok("4D00");  // 2워드를 물었는데 1워드
		assertThatThrownBy(() -> driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, binary, PlcOperation.Kind.READ,
			List.of(point("a", "D0", PointType.INT32)), Map.of(), "t"), (id, f) -> fewer)).hasMessageContaining("바이트여야");
	}

	@Test
	void ASCII_프레임() throws ToolControlException {
		Map<String, Object> ascii = driver.normalizeSettings(Map.of("frame", "3E_ASCII"), null);
		List<String> sent = new ArrayList<>();
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, ascii, PlcOperation.Kind.READ,
			List.of(point("a", "D100", PointType.INT16, 2, 0)), Map.of(), "t"), (id, f) -> {
				sent.add(new String(f, StandardCharsets.US_ASCII));
				return "D00000FF03FF00000C0000004D0001".getBytes(StandardCharsets.US_ASCII);
			});
		assertThat(sent).containsExactly("500000FF03FF000018" + "0010" + "0401" + "0000" + "D*" + "000100" + "0002");
		assertThat(r.values().get("a")).isEqualTo(List.of(77, 1));
	}
}
