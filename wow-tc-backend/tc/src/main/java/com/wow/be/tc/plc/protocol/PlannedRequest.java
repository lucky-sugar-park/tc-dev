package com.wow.be.tc.plc.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.interfs.PointInfo;

/**
 * 작업을 PLC 에 보낼 요청 하나 — 보내지 않고 나눠 본 것(docs/interface-detail-design.md §7.1). 실행과 같은 길로 만든다.
 *
 * @param seq      보내는 차례(1부터)
 * @param function 명령·함수(예 {@code 0401/0000 일괄 읽기(워드)}, {@code FC3 Read Holding Registers})
 * @param target   시작 주소(예 {@code D100}, {@code HR0})
 * @param count    개수
 * @param unit     개수의 단위(워드·비트·레지스터·코일)
 * @param points   이 요청에 담긴 점 이름
 * @param frameHex 요청 프레임 16진. 4E 일련번호·Modbus 트랜잭션 ID 는 보낼 때 채우므로 0 이다
 */
public record PlannedRequest(int seq, String function, String target, int count, String unit, List<String> points, String frameHex) {

	public static PlannedRequest of(int seq, String function, String target, int count, String unit, List<String> points, byte[] frame) {
		return new PlannedRequest(seq, function, target, count, unit, points, HexFormat.of().withUpperCase().formatHex(frame));
	}

	/** 미리보기 쓰기 값 — 받은 값이 있으면 그것, 없으면 형의 0 값(BOOL false·문자열 빈 값·BYTES 0 바이트). 배열이면 개수만큼 */
	public static Map<String, Object> withSampleValues(List<PointInfo> points, Map<String, Object> values) {
		Map<String, Object> out = new LinkedHashMap<>();
		for (PointInfo p : points) {
			Object given = values != null ? values.get(p.getName()) : null;
			out.put(p.getName(), given != null ? given : sample(p));
		}
		return out;
	}

	private static Object sample(PointInfo p) {
		Object one = switch (p.getType()) {
			case BOOL -> Boolean.FALSE;
			case STRING -> "";
			case BYTES -> "00".repeat(Math.max(p.getLength(), 0));
			case FLOAT32, FLOAT64 -> 0.0;
			default -> 0;
		};
		int count = p.getCount();
		if (count <= 1 || p.getType() == PointType.STRING || p.getType() == PointType.BYTES) {
			return one;
		}
		return new ArrayList<>(Collections.nCopies(count, one));
	}
}
