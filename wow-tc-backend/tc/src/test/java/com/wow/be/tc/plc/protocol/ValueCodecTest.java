package com.wow.be.tc.plc.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 점 값 ↔ 레지스터 바이트 (docs/plc-protocol-design.md §5) */
class ValueCodecTest {

	private static final ByteOrder L = ByteOrder.LITTLE;

	private static final ByteOrder B = ByteOrder.BIG;

	private static byte[] hex(String s) {
		return HexFormat.of().parseHex(s);
	}

	private static String hex(byte[] b) {
		return HexFormat.of().withUpperCase().formatHex(b);
	}

	@Test
	void MELSEC_리틀엔디안_정수() {
		assertThat(ValueCodec.decode(PointType.INT16, 1, 0, hex("4D00"), 0, L, L)).isEqualTo(77);
		assertThat(ValueCodec.decode(PointType.INT16, 1, 0, hex("FFFF"), 0, L, L)).isEqualTo(-1);
		assertThat(ValueCodec.decode(PointType.UINT16, 1, 0, hex("FFFF"), 0, L, L)).isEqualTo(65535);
		// 32비트 = 하위 워드 먼저
		assertThat(ValueCodec.decode(PointType.INT32, 1, 0, hex("78563412"), 0, L, L)).isEqualTo(0x12345678);
		assertThat(hex(ValueCodec.encode(PointType.INT32, 1, 0, 0x12345678, L, L, "v"))).isEqualTo("78563412");
	}

	@Test
	void Modbus_빅엔디안과_워드_순서() {
		// 0x12345678 — 바이트·워드 BIG: 12 34 56 78, 워드 순서만 LITTLE(CDAB): 56 78 12 34
		assertThat(hex(ValueCodec.encode(PointType.UINT32, 1, 0, 0x12345678L, B, B, "v"))).isEqualTo("12345678");
		assertThat(hex(ValueCodec.encode(PointType.UINT32, 1, 0, 0x12345678L, B, L, "v"))).isEqualTo("56781234");
		assertThat(ValueCodec.decode(PointType.UINT32, 1, 0, hex("56781234"), 0, B, L)).isEqualTo(0x12345678L);
	}

	@Test
	void 실수와_64비트() {
		byte[] f = ValueCodec.encode(PointType.FLOAT32, 1, 0, 1.5, L, L, "v");
		assertThat(ValueCodec.decode(PointType.FLOAT32, 1, 0, f, 0, L, L)).isEqualTo(1.5f);
		byte[] d = ValueCodec.encode(PointType.FLOAT64, 1, 0, "-2.25", B, B, "v");
		assertThat(ValueCodec.decode(PointType.FLOAT64, 1, 0, d, 0, B, B)).isEqualTo(-2.25);
		byte[] l = ValueCodec.encode(PointType.INT64, 1, 0, Long.MIN_VALUE, L, L, "v");
		assertThat(ValueCodec.decode(PointType.INT64, 1, 0, l, 0, L, L)).isEqualTo(Long.MIN_VALUE);
	}

	@Test
	void 배열() {
		byte[] a = ValueCodec.encode(PointType.INT16, 3, 0, List.of(1, -2, 3), L, L, "v");
		assertThat(hex(a)).isEqualTo("0100FEFF0300");
		assertThat(ValueCodec.decode(PointType.INT16, 3, 0, a, 0, L, L)).isEqualTo(List.of(1, -2, 3));
		assertThatThrownBy(() -> ValueCodec.encode(PointType.INT16, 3, 0, List.of(1, 2), L, L, "v"))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("3개");
	}

	@Test
	void 문자열과_바이트는_전선_순서_그대로() {
		byte[] s = ValueCodec.encode(PointType.STRING, 1, 5, "ABC", L, L, "v");
		assertThat(hex(s)).isEqualTo("414243000000");  // 5바이트 → 워드 맞춤 6바이트
		assertThat(ValueCodec.decode(PointType.STRING, 1, 5, s, 0, L, L)).isEqualTo("ABC");
		assertThat(ValueCodec.decode(PointType.BYTES, 1, 2, hex("0AFF"), 0, L, L)).isEqualTo("0AFF");
		assertThatThrownBy(() -> ValueCodec.encode(PointType.STRING, 1, 2, "ABC", L, L, "v"))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("길이");
	}

	@Test
	void 잘못된_쓰기_값은_거절() {
		assertThatThrownBy(() -> ValueCodec.encode(PointType.INT16, 1, 0, 40000, L, L, "v")).hasMessageContaining("범위");
		assertThatThrownBy(() -> ValueCodec.encode(PointType.UINT16, 1, 0, -1, L, L, "v")).hasMessageContaining("범위");
		assertThatThrownBy(() -> ValueCodec.encode(PointType.INT16, 1, 0, "1.5", L, L, "v")).hasMessageContaining("정수");
		assertThatThrownBy(() -> ValueCodec.encode(PointType.INT16, 1, 0, "abc", L, L, "v")).hasMessageContaining("정수");
		assertThat(hex(ValueCodec.encode(PointType.INT16, 1, 0, "12", L, L, "v"))).isEqualTo("0C00");
		assertThat(ValueCodec.bool("1", "b")).isTrue();
		assertThatThrownBy(() -> ValueCodec.bool("yes?", "b")).hasMessageContaining("BOOL");
	}
}
