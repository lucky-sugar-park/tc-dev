package com.wow.be.tc.plc.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 인터페이스 settings (docs/interface-detail-design.md §3) — 프로토콜 설명의 {@code interfaceSettings} 목록으로 검증하고, 커넥터 설정 위에 덮는다.
 * 화면 폼과 서버 검증이 같은 목록을 쓴다.
 */
public final class InterfaceSettings {

	private InterfaceSettings() {
	}

	/**
	 * 목록에 있는 키만 받는다. NUMBER 는 범위 안 정수(16진 0x… 도), ENUM 은 고를 값 중 하나(대소문자 무시). 빈 값은 뺀다(커넥터 값을 쓴다).
	 *
	 * @param label 오류 문구에 쓸 프로토콜 이름
	 */
	public static Map<String, Object> normalize(String label, List<ProtocolDescriptor.Setting> schema, Map<String, Object> in) {
		Map<String, Object> out = new LinkedHashMap<>();
		if (in == null || in.isEmpty()) {
			return out;
		}
		Map<String, ProtocolDescriptor.Setting> byKey = schema.stream()
			.collect(Collectors.toMap(ProtocolDescriptor.Setting::key, s -> s, (a, b) -> a, LinkedHashMap::new));
		for (Map.Entry<String, Object> e : in.entrySet()) {
			ProtocolDescriptor.Setting s = byKey.get(e.getKey());
			if (s == null) {
				throw new IllegalArgumentException(label + " 인터페이스 설정에 없는 항목입니다: " + e.getKey() + " (" + String.join(", ", byKey.keySet()) + ")");
			}
			Object v = e.getValue();
			if (v == null || v.toString().isBlank()) {
				continue;
			}
			out.put(s.key(), "ENUM".equals(s.type()) ? option(label, s, v) : number(label, s, v));
		}
		return out;
	}

	/** 커넥터 설정(정규화한 것) 위에 인터페이스 settings(정규화한 것)를 덮는다 */
	public static Map<String, Object> merge(Map<String, Object> connector, Map<String, Object> overrides) {
		Map<String, Object> out = new LinkedHashMap<>(connector != null ? connector : Map.of());
		if (overrides != null) {
			out.putAll(overrides);
		}
		return out;
	}

	private static Object option(String label, ProtocolDescriptor.Setting s, Object v) {
		String text = v.toString().trim();
		for (String o : s.options()) {
			if (o.equalsIgnoreCase(text)) {
				// 숫자로만 된 고를 값(addressBase 0·1 같은)은 숫자로 둔다 — 드라이버가 숫자로 읽는다
				return o.matches("\\d+") ? Integer.valueOf(o) : o.toUpperCase(Locale.ROOT);
			}
		}
		throw new IllegalArgumentException(label + " 인터페이스 설정 " + s.key() + " 은 " + s.options() + " 중 하나입니다: " + v);
	}

	private static int number(String label, ProtocolDescriptor.Setting s, Object v) {
		int n;
		try {
			String text = v.toString().trim();
			n = v instanceof Number num ? num.intValue()
				: s.hex() && text.toLowerCase(Locale.ROOT).startsWith("0x") ? Integer.parseInt(text.substring(2), 16) : Integer.parseInt(text);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(label + " 인터페이스 설정 " + s.key() + " 은 숫자여야 합니다" + (s.hex() ? "(16진은 0x…)" : "") + ": " + v);
		}
		if ((s.min() != null && n < s.min()) || (s.max() != null && n > s.max())) {
			throw new IllegalArgumentException(label + " 인터페이스 설정 " + s.key() + " 은 " + s.min() + "~" + s.max() + " 입니다: " + v);
		}
		return n;
	}
}
