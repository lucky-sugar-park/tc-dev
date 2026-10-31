package com.wow.be.tc.plc.protocol;

import java.util.Map;

/**
 * 작업 결과 — 읽은 값(점 이름 → 값)과 PLC 상태. 상태가 정상이 아니면 값은 그때까지 읽은 것만 들어 있을 수 있다.
 */
public record PlcResult(Map<String, Object> values, PlcStatus status) {
}
