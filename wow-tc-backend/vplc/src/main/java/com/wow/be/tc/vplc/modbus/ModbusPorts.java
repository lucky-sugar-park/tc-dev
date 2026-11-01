package com.wow.be.tc.vplc.modbus;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.ConnectionEvents;
import com.wow.be.tc.vplc.modbus.ModbusTcpServer.Client;

/**
 * 포트별 Modbus TCP 서버 — 한 포트에 단위 ID 가 다른 가상 PLC 를 여럿 붙일 수 있다(게이트웨이 뒤 장치들 흉내).
 * 첫 장치가 시작하면 포트를 열고 마지막 장치가 정지하면 닫는다. 연결 한도는 붙은 장치들의 포트당 연결 중 가장 큰 값.
 * 연결·끊김은 그 포트에 붙은 장치마다 연결 이력에 남긴다.
 */
public final class ModbusPorts {

	private static final ModbusPorts INSTANCE = new ModbusPorts();

	private final Map<Integer, Port> ports = new ConcurrentHashMap<>();

	private static final class Port {

		final Map<Integer, ModbusVirtualDevice> units = new ConcurrentHashMap<>();

		ModbusTcpServer server;
	}

	private ModbusPorts() {
	}

	public static ModbusPorts get() {
		return INSTANCE;
	}

	/** 장치를 포트에 붙인다(시작) — 포트가 처음이면 연다 */
	public synchronized void attach(ModbusVirtualDevice d) throws VirtualPlcException {
		Port p = ports.computeIfAbsent(d.port(), k -> new Port());
		ModbusVirtualDevice other = p.units.get(d.unitId());
		if (other != null && other != d) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409,
				"포트 " + d.port() + " 에 단위 ID " + d.unitId() + " 가상 PLC 가 이미 실행 중입니다: " + other.getName());
		}
		p.units.put(d.unitId(), d);
		if (p.server == null) {
			try {
				p.server = new ModbusTcpServer(d.bindAddress(), d.port(), unit -> {
					ModbusVirtualDevice u = p.units.get(unit);
					return u != null ? u.memory() : null;
				}, listener(p), () -> p.units.values().stream().mapToInt(ModbusVirtualDevice::connLimit).max().orElse(1));
			} catch (IOException e) {
				p.units.remove(d.unitId());
				if (p.units.isEmpty()) {
					ports.remove(d.port());
				}
				throw new VirtualPlcException(VirtualPlcException.ERR_409, "Modbus 포트를 열 수 없습니다: " + d.port() + " — " + e.getMessage(), e);
			}
		} else {
			// 이미 붙어 있는 연결은 새 장치에도 연결로 남긴다
			p.server.clients().forEach(d::connected);
		}
	}

	/** 장치를 떼어 낸다(정지) — 마지막 장치면 포트를 닫는다 */
	public synchronized void detach(ModbusVirtualDevice d) {
		Port p = ports.get(d.port());
		if (p == null || p.units.get(d.unitId()) != d) {
			return;
		}
		p.units.remove(d.unitId());
		d.disconnectAll();
		if (p.units.isEmpty()) {
			ports.remove(d.port());
			if (p.server != null) {
				p.server.close();
			}
		}
	}

	/** 그 포트의 연결을 모두 끊는다 */
	public int dropConnections(int port) {
		Port p = ports.get(port);
		return p != null && p.server != null ? p.server.dropConnections() : 0;
	}

	/** 그 포트의 연결 수 */
	public int connections(int port) {
		Port p = ports.get(port);
		return p != null && p.server != null ? p.server.connections() : 0;
	}

	private static ModbusTcpServer.Listener listener(Port p) {
		return new ModbusTcpServer.Listener() {
			@Override
			public void connected(Client c) {
				p.units.values().forEach(d -> d.connected(c));
			}

			@Override
			public void disconnected(Client c) {
				p.units.values().forEach(d -> d.disconnected(c));
			}
		};
	}

	/** 연결 이력 한 줄 */
	static void history(ModbusVirtualDevice d, Client c, boolean connected) {
		ConnectionEvents.dispatch(d.getId(), c.port(), c.remoteIp(), c.id(), connected,
			"Remote client is connected / disconnected to virtual Modbus device (unit " + d.unitId() + ").");
	}
}
