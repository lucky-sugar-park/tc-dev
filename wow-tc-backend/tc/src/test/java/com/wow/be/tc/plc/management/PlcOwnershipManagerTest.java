package com.wow.be.tc.plc.management;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 균형 규칙 — 한 인스턴스가 가질 PLC 수 상한 ceil(게시 수 / 인스턴스 수) (docs/plc-ha-design.md §3.3) */
class PlcOwnershipManagerTest {

	@Test
	void 인스턴스_하나면_전부() {
		assertThat(PlcOwnershipManager.limit(5, 1)).isEqualTo(5);
	}

	@Test
	void 나누어_떨어지지_않으면_올림() {
		assertThat(PlcOwnershipManager.limit(5, 2)).isEqualTo(3);
		assertThat(PlcOwnershipManager.limit(2, 2)).isEqualTo(1);
		assertThat(PlcOwnershipManager.limit(1, 3)).isEqualTo(1);
	}

	@Test
	void 게시가_없으면_0_인스턴스_수가_0이면_하나로_본다() {
		assertThat(PlcOwnershipManager.limit(0, 2)).isZero();
		assertThat(PlcOwnershipManager.limit(3, 0)).isEqualTo(3);
	}
}
