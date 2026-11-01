package com.wow.be.tc.vplc.modbus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.VirtualPlc;
import com.wow.be.tc.vplc.core.VirtualPlcProvider;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;

/**
 * Modbus TCP 가상 PLC 종류 — 설정 {@code unitId}(0~255, 기본 1)·{@code size}(영역마다 칸, 1~65536, 기본 10000)·{@code delayMs}(0~600000).
 * 포트 하나, 포트당 연결 기본 8, 메모리 종류 칸은 HR·IR·CO·DI(화면 표시).
 */
@Component
public class ModbusProvider implements VirtualPlcProvider {

	public static final List<String> AREAS = List.of("HR", "IR", "CO", "DI");

	public static final int DEFAULT_CONN_LIMIT = 8;

	@Override
	public Protocol protocol() {
		return Protocol.MODBUS_TCP;
	}

	@Override
	public void normalize(VirtualPlcInfo info) throws VirtualPlcException {
		VirtualPlcProvider.singlePort(info, DEFAULT_CONN_LIMIT);
		Map<String, Object> s = new LinkedHashMap<>(info.getSettings() != null ? info.getSettings() : Map.of());
		try {
			int unit = ModbusVirtualPlc.intOf(s, "unitId", ModbusVirtualPlc.DEFAULT_UNIT_ID);
			int size = ModbusVirtualPlc.intOf(s, "size", ModbusVirtualPlc.DEFAULT_SIZE);
			int delay = ModbusVirtualPlc.intOf(s, "delayMs", 0);
			if (unit < 0 || unit > 255) {
				throw bad("단위 ID 는 0~255 입니다: " + unit);
			}
			if (size < 1 || size > 65536) {
				throw bad("칸 수는 1~65536 입니다: " + size);
			}
			if (delay < 0 || delay > 600_000) {
				throw bad("응답 지연은 0~600000ms 입니다: " + delay);
			}
			s.put("unitId", unit);
			s.put("size", size);
			s.put("delayMs", delay);
		} catch (NumberFormatException e) {
			throw bad("설정 값이 숫자가 아닙니다: " + e.getMessage());
		}
		info.setSettings(s);
		info.setFrameFormat(null);
		info.setMemoryTypeList(new ArrayList<>(AREAS));
	}

	@Override
	public VirtualPlc create(VirtualPlcInfo info) {
		return new ModbusVirtualPlc(info);
	}

	static VirtualPlcException bad(String message) {
		return new VirtualPlcException(VirtualPlcException.ERR_400, message);
	}
}
