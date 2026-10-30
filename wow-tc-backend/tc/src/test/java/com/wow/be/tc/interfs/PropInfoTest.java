package com.wow.be.tc.interfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.utils.ByteUtil.Endian;

/**
 * PLC 로 쓸 값을 바이트로 바꾸기 (docs/dispatch-redesign.md §4.1.2). 값은 JSON 에서 오므로 숫자는 Integer·Long·Double 중
 * 무엇이든 올 수 있다 — 예전에는 강제 변환으로 SHORT·LONG·DOUBLE 이 늘 실패했다.
 */
class PropInfoTest {

	private static PropInfo prop(PropType type, int length) {
		PropInfo p = new PropInfo();
		p.setName("v");
		p.setType(type);
		p.setLength(length);
		return p;
	}

	@Test
	void SHORT_는_JSON_정수_문자열_정수형_실수를_받는다() {
		PropInfo p = prop(PropType.SHORT, 2);

		assertThat(p.extractRealDataAsBytes(1234, Endian.LITTLE)).containsExactly(0xD2, 0x04);
		assertThat(p.extractRealDataAsBytes("1234", Endian.LITTLE)).containsExactly(0xD2, 0x04);
		assertThat(p.extractRealDataAsBytes(1234.0, Endian.LITTLE)).containsExactly(0xD2, 0x04);
		assertThat(p.extractRealDataAsBytes(-1, Endian.LITTLE)).containsExactly(0xFF, 0xFF);
	}

	@Test
	void SHORT_범위를_넘으면_거부() {
		PropInfo p = prop(PropType.SHORT, 2);

		assertThatThrownBy(() -> p.extractRealDataAsBytes(40000, Endian.LITTLE)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("범위");
		assertThatThrownBy(() -> p.extractRealDataAsBytes(-32769, Endian.LITTLE)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 정수형에_소수는_거부() {
		assertThatThrownBy(() -> prop(PropType.SHORT, 2).extractRealDataAsBytes(1.5, Endian.LITTLE))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("정수");
	}

	@Test
	void 문자열이_숫자가_아니면_NumberFormatException() {
		assertThatThrownBy(() -> prop(PropType.SHORT, 2).extractRealDataAsBytes("abc", Endian.LITTLE))
			.isInstanceOf(NumberFormatException.class);
	}

	@Test
	void LONG_은_JSON_Integer_를_받는다() {
		assertThat(prop(PropType.LONG, 8).extractRealDataAsBytes(7, Endian.LITTLE)).hasSize(8).startsWith(0x07, 0x00);
	}

	@Test
	void INT_와_DOUBLE() {
		assertThat(prop(PropType.INT, 4).extractRealDataAsBytes(1, Endian.LITTLE)).containsExactly(0x01, 0x00, 0x00, 0x00);
		assertThat(prop(PropType.DOUBLE, 8).extractRealDataAsBytes(3, Endian.LITTLE)).hasSize(8);
	}

	@Test
	void BOOLEAN_은_문자열도_받는다() {
		assertThat(prop(PropType.BOOLEAN, 1).extractRealDataAsBytes("true", Endian.LITTLE)).containsExactly(0x01);
		assertThat(prop(PropType.BOOLEAN, 1).extractRealDataAsBytes(false, Endian.LITTLE)).containsExactly(0x00);
	}
}
