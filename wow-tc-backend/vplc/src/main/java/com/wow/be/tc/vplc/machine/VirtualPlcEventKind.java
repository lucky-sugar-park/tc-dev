package com.wow.be.tc.vplc.machine;

/**
 * 가상 PLC 가 화면(SSE)으로 보내는 이벤트 이름.
 *
 * <p>레거시는 자체 {@code PushEventPayload.PushEventKind} enum 에 두었다. framework {@code PushEventPayload} 의
 * eventKind 는 문자열이라, vplc 전용 이름만 여기 모으고 {@link #getName()} 값을 넣는다(SSE 이벤트 이름은 그대로).
 */
public enum VirtualPlcEventKind {

	VPLC_STATUS_CHANGED("vplc_status_changed"),
	VPLC_MEM_VALUE_UPDATED("vplc_mem_value_updated"),
	VPLC_STARTED("vplc_started"),
	VPLC_STOPPED("vplc_stopped"),
	VPLC_PAUSED("vplc_paused"),
	VPLC_RESUMED("vplc_resumed"),
	CONNECTED("connected"),
	COUNT("count");

	private final String name;

	private VirtualPlcEventKind(String name) {
		this.name=name;
	}

	public String getName() {
		return this.name;
	}
}
