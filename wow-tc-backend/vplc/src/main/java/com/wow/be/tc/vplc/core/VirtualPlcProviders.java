package com.wow.be.tc.vplc.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;

/** 빈으로 모은 {@link VirtualPlcProvider} 들 — 종류로 찾는다(종류가 비어 있으면 MELSEC_MC, 예전 줄) */
@Component
public class VirtualPlcProviders {

	private final Map<Protocol, VirtualPlcProvider> byProtocol = new EnumMap<>(Protocol.class);

	public VirtualPlcProviders(List<VirtualPlcProvider> providers) {
		providers.forEach(p -> byProtocol.put(p.protocol(), p));
	}

	public VirtualPlcProvider of(Protocol protocol) throws VirtualPlcException {
		VirtualPlcProvider p = byProtocol.get(protocol != null ? protocol : Protocol.MELSEC_MC);
		if (p == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "지원하지 않는 가상 PLC 종류입니다: " + protocol);
		}
		return p;
	}

	/** 등록·수정 — 종류를 채우고(비면 MELSEC_MC) 그 종류의 규칙으로 */
	public void normalize(VirtualPlcInfo info) throws VirtualPlcException {
		if (info.getProtocol() == null) {
			info.setProtocol(Protocol.MELSEC_MC);
		}
		of(info.getProtocol()).normalize(info);
	}

	public VirtualPlc create(VirtualPlcInfo info) throws VirtualPlcException {
		return of(info.getProtocol()).create(info);
	}

	public void onDelete(VirtualPlcInfo info) throws VirtualPlcException {
		of(info.getProtocol()).onDelete(info.getId());
	}
}
