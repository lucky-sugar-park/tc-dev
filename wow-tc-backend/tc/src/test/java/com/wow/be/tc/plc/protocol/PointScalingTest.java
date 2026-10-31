package com.wow.be.tc.plc.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.interfs.PointInfo;

/** 점 배율·오프셋·쓰기 범위 (docs/interface-detail-design.md §9.2) */
class PointScalingTest {

	private static PointInfo point(String name, PointType type, Double scale, Double offset, Double min, Double max) {
		PointInfo p = new PointInfo();
		p.setName(name);
		p.setAddress("D0");
		p.setType(type);
		p.setScale(scale);
		p.setOffset(offset);
		p.setMin(min);
		p.setMax(max);
		return p;
	}

	@Test
	void 읽기는_원시에_배율을_곱하고_오프셋을_더한다() {
		PointInfo temp = point("temp", PointType.INT16, 0.1, null, null, null);
		PointInfo press = point("press", PointType.UINT16, 0.01, -1.0, null, null);
		PointInfo plain = point("count", PointType.INT32, null, null, null, null);
		Map<String, Object> out = PointScaling.toEngineering(List.of(temp, press, plain),
			Map.of("temp", 253, "press", 250, "count", 7));
		assertThat(out).containsEntry("temp", 25.3).containsEntry("press", 1.5).containsEntry("count", 7);
		// 배열은 원소마다, FLOAT32 도
		PointInfo arr = point("arr", PointType.FLOAT32, 2.0, null, null, null);
		assertThat(PointScaling.toEngineering(List.of(arr), Map.of("arr", List.of(1.5f, 0.25f))).get("arr")).isEqualTo(List.of(3.0, 0.5));
	}

	@Test
	void 쓰기는_범위를_보고_거꾸로_풀어_정수형은_반올림() {
		PointInfo temp = point("temp", PointType.INT16, 0.1, null, 0.0, 100.0);
		assertThat(PointScaling.toRaw(List.of(temp), Map.of("temp", 25.3))).containsEntry("temp", 253L);
		assertThat(PointScaling.toRaw(List.of(temp), Map.of("temp", "25.36"))).containsEntry("temp", 254L);
		assertThatThrownBy(() -> PointScaling.toRaw(List.of(temp), Map.of("temp", 100.1))).hasMessageContaining("범위");
		assertThatThrownBy(() -> PointScaling.toRaw(List.of(temp), Map.of("temp", "abc"))).hasMessageContaining("숫자");
		// 배율 없이 범위만 — 값은 그대로
		PointInfo limit = point("speed", PointType.UINT16, null, null, null, 1500.0);
		assertThat(PointScaling.toRaw(List.of(limit), Map.of("speed", 1200))).containsEntry("speed", 1200);
		assertThatThrownBy(() -> PointScaling.toRaw(List.of(limit), Map.of("speed", 1501))).hasMessageContaining("범위");
		PointInfo f = point("f", PointType.FLOAT32, 0.5, 10.0, null, null);
		assertThat(PointScaling.toRaw(List.of(f), Map.of("f", 11))).containsEntry("f", 2.0);
	}

	@Test
	void 정의_검증() {
		assertThatThrownBy(() -> PointScaling.validate(point("b", PointType.BOOL, 2.0, null, null, null))).hasMessageContaining("숫자형");
		assertThatThrownBy(() -> PointScaling.validate(point("s", PointType.STRING, null, null, 0.0, null))).hasMessageContaining("숫자형");
		assertThatThrownBy(() -> PointScaling.validate(point("z", PointType.INT16, 0.0, null, null, null))).hasMessageContaining("0 이 아니어야");
		assertThatThrownBy(() -> PointScaling.validate(point("m", PointType.INT16, null, null, 5.0, 1.0))).hasMessageContaining("최대");
		PointScaling.validate(point("ok", PointType.INT16, 0.1, 0.0, 0.0, 100.0));
		PointScaling.validate(point("none", PointType.BOOL, null, null, null, null));
	}
}
