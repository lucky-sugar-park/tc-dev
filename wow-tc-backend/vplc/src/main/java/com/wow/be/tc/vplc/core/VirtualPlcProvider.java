package com.wow.be.tc.vplc.core;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;

/**
 * 가상 PLC 종류 하나(docs/vplc-architecture.md §2.1) — 각 종류 패키지가 Spring 빈으로 낸다. 공통 코드는 {@link VirtualPlcProviders} 로
 * 종류를 찾을 뿐 종류를 모른다. 종류를 더하려면 패키지 하나와 이 빈 하나.
 */
public interface VirtualPlcProvider {

	Protocol protocol();

	/** 등록·수정 — 그 종류의 기본값을 채우고 검사한다(틀리면 400) */
	void normalize(VirtualPlcInfo info) throws VirtualPlcException;

	/** 게시 — 메모리에 올릴 객체(포트는 아직 열지 않는다) */
	VirtualPlc create(VirtualPlcInfo info) throws VirtualPlcException;

	/** 지울 때 그 종류의 뒷정리 */
	default void onDelete(String vplcId) {
	}

	/** 포트 하나짜리 종류(Modbus·OPC UA) — 포트 수 1, 포트당 연결 기본값, 포트 범위 검사 */
	static void singlePort(VirtualPlcInfo info, int defaultConnLimit) throws VirtualPlcException {
		info.setPortCount(1);
		if (info.getPortConnLimit() <= 0) {
			info.setPortConnLimit(defaultConnLimit);
		}
		if (info.getStartPort() < 1 || info.getStartPort() > 65535) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "포트는 1~65535 입니다: " + info.getStartPort());
		}
	}
}
