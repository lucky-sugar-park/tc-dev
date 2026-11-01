package com.wow.be.tc.vplc.core.memory;

import java.util.List;

import com.wow.be.tc.vplc.VirtualPlcException;

/**
 * 태그 메모리 능력 — 이름 붙은 값(OPC UA, docs/vplc-architecture.md §2.3). 태그 정의는 DB 에 남고 지금 값은 게시된 가상 PLC 에 있으므로
 * 가상 PLC 객체가 아니라 그 종류의 {@code VirtualPlcProvider} 가 구현한다(게시 전에도 정의를 다룬다).
 */
public interface TagMemory {

	/** 값 흉내 — kind: RAMP·SINE·TOGGLE·RANDOM, periodMs 마다, min~max */
	record Simulation(String kind, Long periodMs, Double min, Double max) {
	}

	/** 태그 정의 */
	record TagDef(String path, String type, Object value, Simulation simulation, String description) {
	}

	/** 태그 — 실행 중이면 지금 값, 아니면 처음 값(nodeId 는 실행 중일 때만) */
	record Tag(String path, String nodeId, String expandedNodeId, String type, Object value, Simulation simulation, String description) {
	}

	List<Tag> tags(String vplcId) throws VirtualPlcException;

	void addTag(String vplcId, TagDef def) throws VirtualPlcException;

	void removeTag(String vplcId, String path) throws VirtualPlcException;

	/** 지금 값 — 실행 중일 때만 */
	Tag setValue(String vplcId, String path, Object value) throws VirtualPlcException;
}
