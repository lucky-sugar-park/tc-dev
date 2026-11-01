package com.wow.be.tc.vplc.melsec;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcProvider;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;
import com.wow.be.tc.vplc.melsec.MelsecTypes.MemoryType;

/** MELSEC MC 가상 PLC 종류 — 프레임(기본 BINARY)·시작 포트·포트 수·메모리 종류(하나 이상) */
@Component
public class MelsecProvider implements VirtualPlcProvider {

	@Override
	public Protocol protocol() {
		return Protocol.MELSEC_MC;
	}

	@Override
	public void normalize(VirtualPlcInfo info) throws VirtualPlcException {
		if (info.getMemoryTypeList() == null || info.getMemoryTypeList().isEmpty()) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "메모리 종류를 하나 이상 고르세요");
		}
		if (info.getFrameFormat() == null) {
			info.setFrameFormat(FrameFormat.BINARY);
		}
		info.setSettings(null);
	}

	@Override
	public VirtualPlc create(VirtualPlcInfo info) {
		List<MemoryInfo> mems = new ArrayList<>();
		info.getMemoryTypeList().forEach(t -> {
			MemoryInfo m = new MemoryInfo();
			m.setMemoryType(MemoryType.toMemoryType(t));
			mems.add(m);
		});
		return new MelsecVirtualPlcBuilder()
			.withId(info.getId())
			.withName(info.getName())
			.withIpAddress(info.getIpAddress())
			.withStartPort(info.getStartPort())
			.withFrameFormat(info.getFrameFormat())
			.withPortCount(info.getPortCount())
			.withConnLimit(info.getPortConnLimit())
			.withMemoryTypes(mems)
			.withManufacturer(info.getManufacturer())
			.withDescription(info.getDescription())
			.build();
	}
}
