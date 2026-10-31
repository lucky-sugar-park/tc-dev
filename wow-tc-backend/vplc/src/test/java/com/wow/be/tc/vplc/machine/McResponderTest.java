package com.wow.be.tc.vplc.machine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.MemoryType;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.transport.McCodec;
import com.wow.be.tc.vplc.transport.McCodec.McRequest;

/** 가상 PLC 의 MC 요청 처리 — 프레임 해석(McCodec)과 응답(McResponder). 네트워크 없이 바이트로 (#18) */
class McResponderTest {

	private VirtualProgrammableController vplc;

	@BeforeEach
	void setUp() {
		MemoryInfo d = new MemoryInfo();
		d.setMemoryType(MemoryType.D);
		MemoryInfo m = new MemoryInfo();
		m.setMemoryType(MemoryType.M);
		// 포트를 열지 않는다 — start() 를 부르지 않으면 부트스트래퍼는 만들기만 한다
		vplc = new VirtualProgrammableController("t", "t", "127.0.0.1", PlcStatus.UNKNOWN, FrameFormat.BINARY, List.of(d, m), 0, 1, 1);
	}

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s.replace(" ", ""));
	}

	private static String hex(byte[] b) {
		return HexFormat.of().withUpperCase().formatHex(b);
	}

	/** 3E 바이너리 요청 — 머리(국 번호 기본) + 길이 + 감시 타이머 0010 + 명령·서브 + 본문 */
	private static byte[] binary(String cmdSubAndBody) {
		byte[] body = hex(cmdSubAndBody);
		int len = 2 + body.length;
		return hex(String.format("500000FFFF0300%02X%02X1000", len & 0xFF, len >> 8) + cmdSubAndBody.replace(" ", ""));
	}

	private byte[] handle(byte[] frame, boolean ascii) {
		McRequest r = McCodec.parse(frame, ascii);
		return McResponder.handle(r, vplc);
	}

	/** 바이너리 응답의 종료 코드(리틀엔디안) */
	private static int endCode(byte[] response) {
		return (response[9] & 0xFF) | (response[10] & 0xFF) << 8;
	}

	@Test
	void 요청_길이_덜_오면_0_머리가_아니면_음수() {
		byte[] f = binary("0104 0000 640000 A8 0200");
		assertThat(McCodec.requestLength(f, f.length, false)).isEqualTo(f.length);
		assertThat(McCodec.requestLength(f, 5, false)).isZero();
		assertThat(McCodec.requestLength(f, f.length - 1, false)).isZero();
		assertThat(McCodec.requestLength(hex("1234"), 2, false)).isEqualTo(-1);
		byte[] a = "500000FF03FF000018001004010000D*0001000002".getBytes(StandardCharsets.US_ASCII);
		assertThat(McCodec.requestLength(a, a.length, true)).isEqualTo(a.length);
	}

	@Test
	void 워드_쓰고_읽기_바이너리() throws Exception {
		// D100 부터 2워드 쓰기(0x004D, 0x0001)
		byte[] w = handle(binary("0114 0000 640000 A8 0200 4D00 0100"), false);
		assertThat(endCode(w)).isZero();
		byte[] r = handle(binary("0104 0000 640000 A8 0200"), false);
		assertThat(hex(r)).isEqualTo("D00000FFFF0300060000004D000100");
		assertThat(vplc.read(100, 2, MemoryType.D)).containsExactly(0x4D, 0x00, 0x01, 0x00);
	}

	@Test
	void 워드_읽기_ASCII() throws Exception {
		vplc.write(5, 1, new byte[] { (byte) 0x34, (byte) 0x12 }, MemoryType.D);
		byte[] r = handle("500000FF03FF000018001004010000D*0000050001".getBytes(StandardCharsets.US_ASCII), true);
		assertThat(new String(r, StandardCharsets.US_ASCII)).isEqualTo("D00000FF03FF00000800001234");
	}

	@Test
	void 비트_쓰고_읽기() {
		// M10~M12 에 1,0,1 (바이너리 비트는 두 점이 한 바이트 — 앞이 상위 니블)
		byte[] w = handle(binary("0114 0100 0A0000 90 0300 1010"), false);
		assertThat(endCode(w)).isZero();
		assertThat(vplc.memory(MemoryType.M).getWords()[0]).isEqualTo((short) 0x1400);   // 비트 10, 12
		byte[] r = handle(binary("0104 0100 0A0000 90 0300"), false);
		assertThat(endCode(r)).isZero();
		assertThat(hex(r)).endsWith("1010");
	}

	@Test
	void 루프백은_받은_데이터를_그대로() {
		byte[] r = handle(binary("1906 0000 0500 3132333435"), false);
		assertThat(endCode(r)).isZero();
		assertThat(hex(r)).endsWith("3132333435");
	}

	@Test
	void 오류_종료_코드() {
		// 만들지 않은 메모리(W) → C056
		assertThat(endCode(handle(binary("0104 0000 000000 B4 0100"), false))).isEqualTo(McResponder.ERR_DEVICE);
		// 범위 밖 → C056
		assertThat(endCode(handle(binary("0104 0000 FFFF00 A8 0200"), false))).isEqualTo(McResponder.ERR_DEVICE);
		// 워드 디바이스를 비트 단위로 → C05B
		assertThat(endCode(handle(binary("0104 0100 000000 A8 0100"), false))).isEqualTo(McResponder.ERR_UNIT);
		// 모르는 명령 → C059
		assertThat(endCode(handle(binary("9999 0000 000000 A8 0100"), false))).isEqualTo(McResponder.ERR_COMMAND);
	}

	@Test
	void _4E_프레임은_일련번호를_돌려준다() {
		byte[] body = hex("0104 0000 640000 A8 0100");
		int len = 2 + body.length;
		byte[] f = hex(String.format("54003412000000FFFF0300%02X%02X1000", len & 0xFF, len >> 8) + "01040000640000A80100");
		McRequest req = McCodec.parse(f, false);
		assertThat(req.fourE()).isTrue();
		assertThat(req.serial()).isEqualTo(0x1234);
		byte[] r = McResponder.handle(req, vplc);
		assertThat(hex(r)).startsWith("D4003412");
	}
}
