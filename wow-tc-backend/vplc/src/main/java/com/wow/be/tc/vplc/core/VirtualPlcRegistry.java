package com.wow.be.tc.vplc.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wow.be.tc.vplc.VirtualPlcException;

import lombok.extern.slf4j.Slf4j;

/**
 * 게시된(메모리에 올라온) 가상 PLC 들 — 종류와 관계없이 아이디로(docs/vplc-architecture.md).
 * 게시하면 등록하고, 해제·삭제하면 내린다(내릴 때 해제 — 포트를 닫는다).
 */
@Slf4j
public final class VirtualPlcRegistry {

	private static final VirtualPlcRegistry INSTANCE = new VirtualPlcRegistry();

	private final Map<String, VirtualPlc> plcs = new ConcurrentHashMap<>();

	private VirtualPlcRegistry() {
	}

	public static VirtualPlcRegistry instance() {
		return INSTANCE;
	}

	public void register(VirtualPlc plc) {
		plcs.put(plc.getId(), plc);
	}

	/** 게시된 가상 PLC — 없으면 null */
	public VirtualPlc get(String id) {
		return plcs.get(id);
	}

	/** 내린다 — 해제(실행 중이면 정지)까지. 없으면 아무것도 하지 않는다 */
	public void release(String id) {
		VirtualPlc plc = plcs.remove(id);
		if (plc == null) {
			return;
		}
		try {
			plc.release();
		} catch (VirtualPlcException e) {
			log.warn("가상 PLC 해제 실패: {} — {}", id, e.getMessage());
		}
	}

	public List<VirtualPlc> all() {
		return List.copyOf(plcs.values());
	}
}
