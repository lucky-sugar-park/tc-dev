package com.wow.be.tc.plc.protocol.melsec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PointType;

/** MC 3E·4E 프레임 — 4E 일련번호, 응답 길이 계산(디코더·연결 시험), 루프백 (#12) */
class McFramesTest {

	private final MelsecDriver driver = new MelsecDriver();

	private static String hex(byte[] b) {
		return HexFormat.of().withUpperCase().formatHex(b);
	}

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s.replace(" ", ""));
	}

	private Map<String, Object> settings(String frame) {
		return driver.normalizeSettings(Map.of("frame", frame), null);
	}

	@Test
	void 사E_요청은_일련번호_자리를_두고_채널이_넣는다() {
		McFrames f = new McFrames(settings("4E_BINARY"));
		byte[] req = f.request(MelsecDriver.CMD_READ, MelsecDriver.SUB_WORD, MelsecDevice.D, 100, 2, null);
		assertThat(hex(req)).isEqualTo("5400" + "0000" + "0000" + "00FFFF0300" + "0C00" + "1000" + "0104" + "0000" + "640000A8" + "0200");
		assertThat(McFrames.isFourERequest(req)).isTrue();
		McFrames.setSerial(req, 0x1234);
		assertThat(hex(req)).startsWith("54003412");

		McFrames a = new McFrames(settings("4E_ASCII"));
		byte[] areq = a.request(MelsecDriver.CMD_READ, MelsecDriver.SUB_WORD, MelsecDevice.D, 100, 2, null);
		McFrames.setSerial(areq, 0x00AB);
		assertThat(new String(areq, StandardCharsets.US_ASCII)).startsWith("540000AB0000" + "00FF03FF00" + "0018");
		assertThat(McFrames.isFourERequest(hex("500000FF"))).isFalse();
	}

	@Test
	void 응답의_일련번호() {
		assertThat(McFrames.serialOf(hex("D400 3412 0000 00FFFF0300 0200 0000"))).isEqualTo(0x1234);
		assertThat(McFrames.serialOf("D40000AB000000FF03FF0000040000".getBytes(StandardCharsets.US_ASCII))).isEqualTo(0xAB);
		assertThat(McFrames.serialOf(hex("D000 00FFFF0300 0200 0000"))).isEqualTo(-1);
	}

	@Test
	void 응답_길이_계산_3E_4E_BINARY_ASCII() {
		byte[] three = hex("D00000FFFF0300 0400 0000 4D00");
		assertThat(McFrames.responseLength(three, three.length, false)).isEqualTo(13);
		assertThat(McFrames.responseLength(three, 10, false)).isZero();           // 덜 왔다
		byte[] four = hex("D400 0100 0000 00FFFF0300 0400 0000 4D00");
		assertThat(McFrames.responseLength(four, four.length, false)).isEqualTo(17);
		byte[] two = new byte[four.length + three.length];                       // 두 프레임이 붙어 와도 하나씩
		System.arraycopy(four, 0, two, 0, four.length);
		System.arraycopy(three, 0, two, four.length, three.length);
		assertThat(McFrames.responseLength(two, two.length, false)).isEqualTo(17);
		assertThat(McFrames.responseLength(hex("5000AAAA"), 4, false)).isEqualTo(-1);   // 응답 머리가 아님

		byte[] ascii3 = "D00000FF03FF00000C0000004D0001".getBytes(StandardCharsets.US_ASCII);
		assertThat(McFrames.responseLength(ascii3, ascii3.length, true)).isEqualTo(ascii3.length);
		byte[] ascii4 = "D40000010000" .concat("00FF03FF00000C0000004D0001").getBytes(StandardCharsets.US_ASCII);
		assertThat(McFrames.responseLength(ascii4, ascii4.length, true)).isEqualTo(ascii4.length);
		assertThat(McFrames.responseLength(ascii4, 20, true)).isZero();
	}

	@Test
	void 사E_읽기_응답을_해석한다() throws ToolControlException {
		PointInfo p = new PointInfo();
		p.setName("d100");
		p.setAddress("D100");
		p.setType(PointType.INT16);
		PlcResult r = driver.execute(new PlcOperation(MelsecDriver.PROTOCOL, settings("4E_BINARY"), PlcOperation.Kind.READ, List.of(p),
			Map.of(), "t"), (id, frame) -> hex("D400 0000 0000 00FFFF0300 0400 0000 4D00"));
		assertThat(r.values()).containsEntry("d100", 77);
	}

	@Test
	void 루프백() {
		McFrames f = new McFrames(settings("3E_BINARY"));
		assertThat(hex(f.loopback())).isEqualTo("500000FFFF0300" + "0D00" + "1000" + "1906" + "0000" + "0500" + "4142434445");
		assertThat(f.loopbackOk(hex("D00000FFFF0300 0900 0000 0500 4142434445"))).isTrue();
		assertThat(f.loopbackOk(hex("D00000FFFF0300 0900 0000 0500 4142434446"))).isFalse();   // 다른 데이터
		assertThat(f.loopbackOk(hex("D00000FFFF0300 0B00 59C0 00FFFF0300 1906 0000"))).isFalse(); // 오류 종료 코드

		McFrames a = new McFrames(settings("3E_ASCII"));
		assertThat(new String(a.loopback(), StandardCharsets.US_ASCII)).isEqualTo("500000FF03FF00" + "0015" + "0010" + "0619" + "0000" + "0005" + "ABCDE");
		assertThat(a.loopbackOk("D00000FF03FF00000D00000005ABCDE".getBytes(StandardCharsets.US_ASCII))).isTrue();
	}
}
