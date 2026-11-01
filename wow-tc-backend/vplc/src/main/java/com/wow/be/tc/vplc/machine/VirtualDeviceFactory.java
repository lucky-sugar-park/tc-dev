package com.wow.be.tc.vplc.machine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.MemoryType;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.modbus.ModbusVirtualDevice;
import com.wow.be.tc.vplc.opcua.OpcUaTagEntity;
import com.wow.be.tc.vplc.opcua.OpcUaTagRepository;
import com.wow.be.tc.vplc.opcua.OpcUaVirtualDevice;

/**
 * 가상 PLC 종류(docs/virtual-devices.md)별로 — 등록·수정 때 설정을 맞추고({@link #normalize}), 게시 때 메모리에 올릴 객체를 만든다({@link #create}).
 */
@Component
@EnableConfigurationProperties(VirtualDeviceFactory.OpcUaProperties.class)
public class VirtualDeviceFactory {

	/** Modbus 영역 — 메모리 종류 칸에 둔다(화면 표시용) */
	public static final List<String> MODBUS_AREAS = List.of("HR", "IR", "CO", "DI");

	/** OPC UA 는 메모리 종류 대신 태그 */
	public static final List<String> OPCUA_AREAS = List.of("TAG");

	/** Modbus·OPC UA 의 포트당 연결 기본값(MELSEC 은 1) */
	public static final int DEFAULT_CONN_LIMIT = 8;

	/** 가상 OPC UA 서버의 로그인 사용자(시험용, 모든 OPC UA 가상 PLC 가 같이 쓴다) — vplc.opcua.users */
	@ConfigurationProperties(prefix = "vplc.opcua")
	public record OpcUaProperties(List<User> users) {

		public OpcUaProperties {
			users = users != null ? users : List.of();
		}

		public record User(String username, String password) {
		}

		public Map<String, String> userMap() {
			Map<String, String> m = new LinkedHashMap<>();
			users.forEach(u -> m.put(u.username(), u.password()));
			return m;
		}
	}

	@Autowired
	private OpcUaTagRepository tagRepository;

	@Autowired
	private OpcUaProperties opcUaProperties;

	/** 게시 — 종류별 객체(포트는 아직 열지 않는다) */
	public VirtualDevice create(VirtualPlcInfo info) throws VirtualPlcException {
		Protocol protocol = info.getProtocol() != null ? info.getProtocol() : Protocol.MELSEC_MC;
		return switch (protocol) {
			case MODBUS_TCP -> new ModbusVirtualDevice(info);
			case OPC_UA -> new OpcUaVirtualDevice(info,
				tagRepository.findByVplcIdOrderByPath(info.getId()).stream().map(OpcUaTagEntity::spec).toList(), opcUaProperties.userMap());
			case MELSEC_MC -> {
				List<MemoryInfo> mems = new ArrayList<>();
				info.getMemoryTypeList().forEach(t -> {
					MemoryInfo m = new MemoryInfo();
					m.setMemoryType(MemoryType.toMemoryType(t));
					mems.add(m);
				});
				yield new VirtualProgrammableControllerBuilder()
					.withId(info.getId())
					.withName(info.getName())
					.withIpAddress(info.getIpAddress())
					.withStatus(PlcStatus.PUBLISHED)
					.withStartPort(info.getStartPort())
					.withFrameFormat(info.getFrameFormat())
					.withPortCount(info.getPortCount())
					.withConnLimit(info.getPortConnLimit())
					.withMemoryTypes(mems)
					.withManufacturer(info.getManufacturer())
					.withDescription(info.getDescription())
					.build();
			}
		};
	}

	/**
	 * 등록·수정 — 종류별 설정을 기본값으로 채우고 검사한다(틀리면 400). Modbus·OPC UA 는 포트 하나, 프레임은 쓰지 않아 BINARY,
	 * 메모리 종류는 고정(HR·IR·CO·DI / TAG).
	 */
	public static void normalize(VirtualPlcInfo info) throws VirtualPlcException {
		if (info.getProtocol() == null) {
			info.setProtocol(Protocol.MELSEC_MC);
		}
		if (info.getProtocol() == Protocol.MELSEC_MC) {
			if (info.getMemoryTypeList() == null || info.getMemoryTypeList().isEmpty()) {
				throw bad("메모리 종류를 하나 이상 고르세요");
			}
			return;
		}
		Map<String, Object> s = new LinkedHashMap<>(info.getSettings() != null ? info.getSettings() : Map.of());
		info.setPortCount(1);
		info.setFrameFormat(FrameFormat.BINARY);
		if (info.getPortConnLimit() <= 0) {
			info.setPortConnLimit(DEFAULT_CONN_LIMIT);
		}
		if (info.getStartPort() < 1 || info.getStartPort() > 65535) {
			throw bad("포트는 1~65535 입니다: " + info.getStartPort());
		}
		try {
			if (info.getProtocol() == Protocol.MODBUS_TCP) {
				int unit = ModbusVirtualDevice.intOf(s, "unitId", ModbusVirtualDevice.DEFAULT_UNIT_ID);
				int size = ModbusVirtualDevice.intOf(s, "size", ModbusVirtualDevice.DEFAULT_SIZE);
				int delay = ModbusVirtualDevice.intOf(s, "delayMs", 0);
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
				info.setMemoryTypeList(new ArrayList<>(MODBUS_AREAS));
			} else {
				String path = s.get("path") != null ? String.valueOf(s.get("path")).trim() : OpcUaVirtualDevice.DEFAULT_PATH;
				if (!path.isEmpty() && !path.matches("/?[A-Za-z0-9_\\-/.]{0,100}")) {
					throw bad("엔드포인트 경로는 영숫자·_ - / . 입니다: " + path);
				}
				s.put("path", path.isEmpty() || path.startsWith("/") ? path : "/" + path);
				s.put("requireLogin", Boolean.parseBoolean(String.valueOf(s.getOrDefault("requireLogin", false))));
				s.put("trustAllClients", Boolean.parseBoolean(String.valueOf(s.getOrDefault("trustAllClients", true))));
				info.setMemoryTypeList(new ArrayList<>(OPCUA_AREAS));
			}
		} catch (NumberFormatException e) {
			throw bad("설정 값이 숫자가 아닙니다: " + e.getMessage());
		}
		info.setSettings(s);
	}

	private static VirtualPlcException bad(String message) {
		return new VirtualPlcException(VirtualPlcException.ERR_400, message);
	}
}
