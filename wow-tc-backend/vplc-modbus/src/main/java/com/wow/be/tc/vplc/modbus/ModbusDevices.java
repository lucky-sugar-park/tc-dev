package com.wow.be.tc.vplc.modbus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * 가상 Modbus 장치 목록과 포트별 서버. 기동 때 설정({@code vplc-modbus.devices})으로 만들고, REST 로 더하고 지운다.
 * 한 포트에 단위 ID 가 다른 장치를 여럿 둘 수 있다(게이트웨이 뒤 장치들 흉내).
 */
@Slf4j
@Component
public class ModbusDevices {

	/** 설정 — 장치 목록과 바인드 주소 */
	@ConfigurationProperties(prefix = "vplc-modbus")
	public record Settings(String bindAddress, List<DeviceSpec> devices) {

		public Settings {
			bindAddress = bindAddress != null ? bindAddress : "127.0.0.1";
			devices = devices != null ? devices : List.of();
		}
	}

	/** 장치 하나의 설정 */
	public record DeviceSpec(String name, int port, Integer unitId, Integer size, Long delayMs) {
	}

	private final Settings settings;

	private final Map<String, ModbusDevice> byName = new ConcurrentHashMap<>();

	/** 포트 → (단위 ID → 장치) */
	private final Map<Integer, Map<Integer, ModbusDevice>> byPort = new ConcurrentHashMap<>();

	private final Map<Integer, ModbusTcpServer> servers = new ConcurrentHashMap<>();

	public ModbusDevices(Settings settings) {
		this.settings = settings;
	}

	@PostConstruct
	void start() throws IOException {
		for (DeviceSpec spec : settings.devices()) {
			add(spec);
		}
	}

	@PreDestroy
	void stop() {
		servers.values().forEach(ModbusTcpServer::close);
	}

	public synchronized ModbusDevice add(DeviceSpec spec) throws IOException {
		if (spec.name() == null || !spec.name().matches("[A-Za-z0-9_.-]{1,64}")) {
			throw new IllegalArgumentException("이름은 영숫자·_ . - 64자까지입니다: " + spec.name());
		}
		if (byName.containsKey(spec.name())) {
			throw new IllegalStateException("이미 있는 장치입니다: " + spec.name());
		}
		int unit = spec.unitId() != null ? spec.unitId() : 1;
		if (unit < 0 || unit > 255 || spec.port() < 1 || spec.port() > 65535) {
			throw new IllegalArgumentException("포트 1~65535, 단위 ID 0~255 입니다");
		}
		Map<Integer, ModbusDevice> units = byPort.computeIfAbsent(spec.port(), p -> new ConcurrentHashMap<>());
		if (units.containsKey(unit)) {
			throw new IllegalStateException("포트 " + spec.port() + " 에 단위 ID " + unit + " 장치가 이미 있습니다: " + units.get(unit).name());
		}
		ModbusDevice d = new ModbusDevice(spec.name(), spec.port(), unit, spec.size() != null ? spec.size() : 10_000);
		if (spec.delayMs() != null) {
			d.delayMs(spec.delayMs());
		}
		if (!servers.containsKey(spec.port())) {
			servers.put(spec.port(), new ModbusTcpServer(settings.bindAddress(), spec.port(), ModbusTcpServer.lookup(units)));
		}
		units.put(unit, d);
		byName.put(d.name(), d);
		log.info("가상 Modbus 장치: {} (포트 {}, 단위 ID {}, 칸 {})", d.name(), d.port(), d.unitId(), d.size());
		return d;
	}

	public synchronized void remove(String name) {
		ModbusDevice d = require(name);
		byName.remove(name);
		Map<Integer, ModbusDevice> units = byPort.get(d.port());
		units.remove(d.unitId());
		if (units.isEmpty()) {
			byPort.remove(d.port());
			ModbusTcpServer s = servers.remove(d.port());
			if (s != null) {
				s.close();
			}
		}
	}

	public Collection<ModbusDevice> all() {
		return new ArrayList<>(byName.values());
	}

	public ModbusDevice require(String name) {
		ModbusDevice d = byName.get(name);
		if (d == null) {
			throw new java.util.NoSuchElementException("없는 장치입니다: " + name);
		}
		return d;
	}

	public String bindAddress() {
		return settings.bindAddress();
	}

	public ModbusTcpServer server(int port) {
		return servers.get(port);
	}
}
