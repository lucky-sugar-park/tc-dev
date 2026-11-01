package com.wow.be.tc.vplc.manipulation.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.VirtualDevice;
import com.wow.be.tc.vplc.machine.VirtualProgrammableControllerManager;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.management.VirtualPlcManagementService;
import com.wow.be.tc.vplc.modbus.ModbusDevice;
import com.wow.be.tc.vplc.modbus.ModbusDevice.Table;
import com.wow.be.tc.vplc.modbus.ModbusVirtualDevice;
import com.wow.be.tc.vplc.opcua.OpcUaTagEntity;
import com.wow.be.tc.vplc.opcua.OpcUaTagRepository;
import com.wow.be.tc.vplc.opcua.OpcUaVirtualDevice;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagSpec;
import com.wow.be.tc.vplc.opcua.VirtualOpcUaServer.TagView;

/**
 * 종류별 메모리·제어(docs/virtual-devices.md) — Modbus 영역 값, OPC UA 태그, 응답 지연, 연결 끊기.
 * 메모리(영역 값·태그 지금 값)는 게시된 동안만 있고, 태그 정의와 설정은 DB 에 남는다.
 */
@Service
public class VirtualDeviceService {

	@Autowired
	private VirtualPlcManagementService management;

	@Autowired
	private OpcUaTagRepository tagRepository;

	private final VirtualProgrammableControllerManager manager = VirtualProgrammableControllerManager.getManager();

	/** 게시된 가상 PLC — 아니면 409(MELSEC 메모리 API 와 같다) */
	private VirtualDevice published(String vplcId) throws VirtualPlcException {
		VirtualDevice d = manager.get(vplcId);
		if (d == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "게시되지 않은 가상 PLC 입니다 — 먼저 게시하세요: " + vplcId);
		}
		return d;
	}

	private ModbusVirtualDevice modbus(String vplcId) throws VirtualPlcException {
		if (published(vplcId) instanceof ModbusVirtualDevice m) {
			return m;
		}
		throw new VirtualPlcException(VirtualPlcException.ERR_400, "Modbus 가상 PLC 가 아닙니다: " + vplcId);
	}

	private VirtualPlcInfo requireOpcUa(String vplcId) throws VirtualPlcException {
		VirtualPlcInfo info = management.findVirtualPlc(vplcId);
		if (info.getProtocol() != Protocol.OPC_UA) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "OPC UA 가상 PLC 가 아닙니다: " + vplcId);
		}
		return info;
	}

	/** 게시된 OPC UA 가상 PLC — 게시 안 됐으면 null(태그 정의는 DB 로) */
	private OpcUaVirtualDevice opcUa(String vplcId) {
		return manager.get(vplcId) instanceof OpcUaVirtualDevice o ? o : null;
	}

	// ---- Modbus ----

	/** 영역 값 — 비트는 true/false, 레지스터는 0~65535. 번호는 프로토콜 주소(0부터) */
	public Map<String, Object> modbusRead(String vplcId, String table, int start, int count) throws VirtualPlcException {
		ModbusDevice d = modbus(vplcId).memory();
		Table t = table(table);
		if (count < 1 || count > 2000 || !d.inRange(start, count)) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "범위 밖입니다(칸 " + d.size() + ", 한 번에 1~2000): " + start + "+" + count);
		}
		List<Object> out = new ArrayList<>(count);
		if (t.isBit()) {
			for (boolean b : d.bits(t, start, count)) {
				out.add(b);
			}
		} else {
			for (short s : d.registers(t, start, count)) {
				out.add(s & 0xFFFF);
			}
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("start", start);
		m.put("values", out);
		return m;
	}

	/** 영역 값 바꾸기 — 입력 영역(DI·IR)도 여기로. 비트는 true/false·1/0, 레지스터는 -32768~65535 */
	public Map<String, Object> modbusWrite(String vplcId, String table, int start, List<Object> values) throws VirtualPlcException {
		ModbusDevice d = modbus(vplcId).memory();
		Table t = table(table);
		if (values == null || values.isEmpty() || !d.inRange(start, values.size())) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "범위 밖이거나 값이 없습니다: " + start + "+" + (values == null ? 0 : values.size()));
		}
		if (t.isBit()) {
			boolean[] bits = new boolean[values.size()];
			for (int i = 0; i < bits.length; i++) {
				Object v = values.get(i);
				bits[i] = Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0) || "1".equals(String.valueOf(v))
					|| "true".equalsIgnoreCase(String.valueOf(v));
			}
			d.setBits(t, start, bits);
		} else {
			short[] regs = new short[values.size()];
			for (int i = 0; i < regs.length; i++) {
				int n;
				try {
					n = values.get(i) instanceof Number num ? num.intValue() : Integer.parseInt(String.valueOf(values.get(i)).trim());
				} catch (NumberFormatException e) {
					throw new VirtualPlcException(VirtualPlcException.ERR_400, "레지스터 값이 숫자가 아닙니다: " + values.get(i));
				}
				if (n < -32768 || n > 65535) {
					throw new VirtualPlcException(VirtualPlcException.ERR_400, "레지스터 값은 -32768~65535 입니다: " + n);
				}
				regs[i] = (short) n;
			}
			d.setRegisters(t, start, regs);
		}
		return modbusRead(vplcId, table, start, values.size());
	}

	private static Table table(String table) throws VirtualPlcException {
		try {
			return Table.of(table);
		} catch (IllegalArgumentException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, e.getMessage());
		}
	}

	/** 응답 지연(ms) — 메모리에 바로, 설정(DB)에도 남긴다 */
	@Transactional
	public VirtualPlcInfo modbusDelay(String vplcId, long delayMs) throws VirtualPlcException {
		VirtualPlcInfo info = management.findVirtualPlc(vplcId);
		if (info.getProtocol() != Protocol.MODBUS_TCP) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "응답 지연은 Modbus 가상 PLC 만: " + vplcId);
		}
		if (delayMs < 0 || delayMs > 600_000) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, "응답 지연은 0~600000ms 입니다: " + delayMs);
		}
		info.getSettings().put("delayMs", delayMs);
		management.updateVirtualPlc(info);
		if (manager.get(vplcId) instanceof ModbusVirtualDevice m) {
			m.memory().delayMs(delayMs);
		}
		return management.findVirtualPlc(vplcId);
	}

	/** 연결 끊기(모든 종류) — 끊은 수 */
	public int dropConnections(String vplcId) throws VirtualPlcException {
		return published(vplcId).dropConnections();
	}

	// ---- OPC UA ----

	/** 엔드포인트·네임스페이스 번호(실행 중일 때)·세션 수·태그 수 */
	public Map<String, Object> opcUaInfo(String vplcId) throws VirtualPlcException {
		VirtualPlcInfo info = requireOpcUa(vplcId);
		OpcUaVirtualDevice d = opcUa(vplcId);
		Map<String, Object> m = new LinkedHashMap<>();
		Object path = info.getSettings().getOrDefault("path", OpcUaVirtualDevice.DEFAULT_PATH);
		m.put("endpointUrl", "opc.tcp://" + info.getIpAddress() + ":" + info.getStartPort() + path);
		m.put("namespaceUri", VirtualOpcUaServer.NAMESPACE_URI);
		m.put("namespaceIndex", d != null ? d.namespaceIndex() : -1);
		m.put("sessions", d != null ? d.sessionCount() : 0);
		m.put("tags", tagRepository.findByVplcIdOrderByPath(vplcId).size());
		return m;
	}

	public List<TagView> opcUaTags(String vplcId) throws VirtualPlcException {
		requireOpcUa(vplcId);
		OpcUaVirtualDevice d = opcUa(vplcId);
		if (d != null) {
			return d.tags();
		}
		return tagRepository.findByVplcIdOrderByPath(vplcId).stream().map(e -> {
			TagSpec t = e.spec();
			return new TagView(t.path(), null, "nsu=" + VirtualOpcUaServer.NAMESPACE_URI + ";s=" + t.path(), t.type(), t.value(), t.simulation(),
				t.description());
		}).toList();
	}

	/** 태그 더하기 — 정의는 DB 에, 게시돼 있으면 메모리(실행 중이면 서버)에도 */
	@Transactional
	public void opcUaAddTag(String vplcId, TagSpec spec) throws VirtualPlcException {
		requireOpcUa(vplcId);
		if (tagRepository.existsById(vplcId + ":" + spec.path())) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "이미 있는 태그입니다: " + spec.path());
		}
		try {
			OpcUaVirtualDevice d = opcUa(vplcId);
			if (d != null) {
				d.addTag(spec);
			} else {
				VirtualOpcUaServer.validate(spec);
			}
		} catch (IllegalArgumentException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, e.getMessage());
		}
		tagRepository.save(OpcUaTagEntity.of(vplcId, spec));
	}

	@Transactional
	public void opcUaRemoveTag(String vplcId, String path) throws VirtualPlcException {
		requireOpcUa(vplcId);
		String id = vplcId + ":" + path;
		if (!tagRepository.existsById(id)) {
			throw new VirtualPlcException(VirtualPlcException.ERR_404, "없는 태그입니다: " + path);
		}
		OpcUaVirtualDevice d = opcUa(vplcId);
		if (d != null) {
			d.removeTag(path);
		}
		tagRepository.deleteById(id);
	}

	/** 지금 값 바꾸기 — 실행 중일 때만(값은 메모리, 정의의 처음 값은 그대로) */
	public TagView opcUaSetValue(String vplcId, String path, Object value) throws VirtualPlcException {
		requireOpcUa(vplcId);
		OpcUaVirtualDevice d = opcUa(vplcId);
		if (d == null) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "게시되지 않은 가상 PLC 입니다 — 먼저 게시·시작하세요: " + vplcId);
		}
		try {
			return d.setValue(path, value);
		} catch (IllegalArgumentException e) {
			throw new VirtualPlcException(VirtualPlcException.ERR_400, e.getMessage());
		}
	}
}
