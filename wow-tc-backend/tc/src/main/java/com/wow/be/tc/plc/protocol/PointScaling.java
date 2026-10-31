package com.wow.be.tc.plc.protocol;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.interfs.PointInfo;

/**
 * 점의 배율·오프셋·쓰기 범위 (docs/interface-detail-design.md §9.2, 결정 I3) — 프로토콜과 상관없다.
 * <ul>
 *   <li>읽기: {@code 공학 값 = 원시 값 × scale + offset}. 배율·오프셋이 있으면 값은 실수(배열은 원소마다).</li>
 *   <li>쓰기: 공학 값을 {@code min}·{@code max} 로 검사 → {@code 원시 = (공학 − offset) ÷ scale} → 정수형은 반올림.</li>
 * </ul>
 * 드라이버·원격 어댑터는 원시 값만 다룬다 — 커맨드가 작업을 만들기 전과 결과를 받은 뒤에 이것을 부른다.
 * 계산은 BigDecimal 로 해 {@code 253 × 0.1} 이 {@code 25.299999…} 가 되지 않게 한다. 잘못된 값은 {@link IllegalArgumentException}(400).
 */
public final class PointScaling {

	private PointScaling() {
	}

	/** 점 정의 검증 — 숫자형에만, scale 0 금지, min ≤ max */
	public static void validate(PointInfo p) {
		boolean any = p.getScale() != null || p.getOffset() != null || p.getMin() != null || p.getMax() != null
			|| (p.getUnit() != null && !p.getUnit().isBlank());
		if (!any) {
			return;
		}
		if (!numeric(p.getType())) {
			throw new IllegalArgumentException(p.getName() + ": 배율·오프셋·단위·범위는 숫자형(INT16~FLOAT64)에만 씁니다 — " + p.getType());
		}
		if (p.getScale() != null && p.getScale() == 0) {
			throw new IllegalArgumentException(p.getName() + ": 배율(scale)은 0 이 아니어야 합니다");
		}
		if (p.getUnit() != null && p.getUnit().length() > 32) {
			throw new IllegalArgumentException(p.getName() + ": 단위는 32자까지입니다");
		}
		if (p.getMin() != null && p.getMax() != null && p.getMin() > p.getMax()) {
			throw new IllegalArgumentException(p.getName() + ": 최소(" + p.getMin() + ")가 최대(" + p.getMax() + ")보다 큽니다");
		}
	}

	/** 읽은 원시 값 → 공학 값. 배율·오프셋이 없는 점은 그대로 */
	public static Map<String, Object> toEngineering(List<PointInfo> points, Map<String, Object> raw) {
		if (raw == null || raw.isEmpty() || points.stream().noneMatch(PointScaling::scaled)) {
			return raw;
		}
		Map<String, Object> out = new LinkedHashMap<>(raw);
		for (PointInfo p : points) {
			if (scaled(p) && out.get(p.getName()) != null) {
				out.put(p.getName(), map(out.get(p.getName()), v -> engineering(p, v)));
			}
		}
		return out;
	}

	/**
	 * 쓰기 값(공학 값) → 원시 값. 값이 있는 점만 바꾼다(없는 점은 드라이버가 거절).
	 * @throws IllegalArgumentException 숫자가 아니거나 범위 밖
	 */
	public static Map<String, Object> toRaw(List<PointInfo> points, Map<String, Object> values) {
		if (values == null || values.isEmpty()) {
			return values;
		}
		Map<String, Object> out = new LinkedHashMap<>(values);
		for (PointInfo p : points) {
			Object v = out.get(p.getName());
			if (v == null || !(scaled(p) || p.getMin() != null || p.getMax() != null)) {
				continue;
			}
			out.put(p.getName(), map(v, one -> raw(p, one)));
		}
		return out;
	}

	private static boolean numeric(PointType t) {
		return t != null && t != PointType.BOOL && !t.hasLength();
	}

	private static boolean scaled(PointInfo p) {
		return numeric(p.getType()) && ((p.getScale() != null && p.getScale() != 1) || (p.getOffset() != null && p.getOffset() != 0));
	}

	private static Object map(Object value, java.util.function.Function<Object, Object> f) {
		if (value instanceof List<?> list) {
			List<Object> out = new ArrayList<>(list.size());
			for (Object o : list) {
				out.add(f.apply(o));
			}
			return out;
		}
		return f.apply(value);
	}

	private static Object engineering(PointInfo p, Object raw) {
		if (!(raw instanceof Number n)) {
			return raw;
		}
		BigDecimal v = decimal(n).multiply(scaleOf(p)).add(offsetOf(p));
		return v.doubleValue();
	}

	private static Object raw(PointInfo p, Object value) {
		BigDecimal eng = number(p, value);
		if (p.getMin() != null && eng.compareTo(BigDecimal.valueOf(p.getMin())) < 0
				|| p.getMax() != null && eng.compareTo(BigDecimal.valueOf(p.getMax())) > 0) {
			throw new IllegalArgumentException(p.getName() + ": " + eng.stripTrailingZeros().toPlainString() + " 는 범위("
				+ (p.getMin() != null ? p.getMin() : "") + "~" + (p.getMax() != null ? p.getMax() : "") + ") 밖입니다");
		}
		if (!scaled(p)) {
			return value;
		}
		BigDecimal raw = eng.subtract(offsetOf(p)).divide(scaleOf(p), MathContext.DECIMAL64);
		return switch (p.getType()) {
			case FLOAT32, FLOAT64 -> raw.doubleValue();
			default -> raw.setScale(0, RoundingMode.HALF_UP).longValueExact();
		};
	}

	private static BigDecimal number(PointInfo p, Object value) {
		try {
			if (value instanceof Number n) {
				return decimal(n);
			}
			if (value instanceof String s && !s.isBlank()) {
				return new BigDecimal(s.trim());
			}
		} catch (NumberFormatException e) {
			// 아래
		}
		throw new IllegalArgumentException(p.getName() + ": 숫자 값이 필요합니다 — " + value);
	}

	/** double 은 문자열로 거쳐 0.1 이 0.1000000000000000055… 가 되지 않게 */
	private static BigDecimal decimal(Number n) {
		return n instanceof BigDecimal d ? d : new BigDecimal(n.toString());
	}

	private static BigDecimal scaleOf(PointInfo p) {
		return p.getScale() != null ? BigDecimal.valueOf(p.getScale()) : BigDecimal.ONE;
	}

	private static BigDecimal offsetOf(PointInfo p) {
		return p.getOffset() != null ? BigDecimal.valueOf(p.getOffset()) : BigDecimal.ZERO;
	}
}
