package com.wow.be.tc.vplc.modbus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.VirtualDevice;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.modbus.ModbusTcpServer.Client;

/**
 * 가상 Modbus TCP 장치 — 포트 + 단위 ID, 네 영역(코일·이산 입력·입력 레지스터·보유 레지스터)을 메모리에 둔다(docs/virtual-devices.md).
 * 시작하면 {@link ModbusPorts} 의 그 포트에 붙고, 일시정지는 답하지 않기, 응답 지연은 설정 {@code delayMs}. 영역 값은 게시돼 있는 동안 남는다.
 */
public final class ModbusVirtualDevice implements VirtualDevice {

	/** 설정 기본값 */
	public static final int DEFAULT_UNIT_ID = 1;

	public static final int DEFAULT_SIZE = 10_000;

	private final String id;

	private final String name;

	private final String bindAddress;

	private final int connLimit;

	private final ModbusDevice memory;

	/** 이 장치가 연결로 남긴 연결(아이디 → 연결) */
	private final Map<String, Client> notified = new ConcurrentHashMap<>();

	private volatile PlcStatus status = PlcStatus.PUBLISHED;

	private volatile boolean attached;

	public ModbusVirtualDevice(VirtualPlcInfo info) {
		this.id = info.getId();
		this.name = info.getName();
		this.bindAddress = info.getIpAddress() != null && !info.getIpAddress().isBlank() ? info.getIpAddress() : "127.0.0.1";
		this.connLimit = Math.max(1, info.getPortConnLimit());
		Map<String, Object> s = info.getSettings();
		this.memory = new ModbusDevice(info.getName(), info.getStartPort(), intOf(s, "unitId", DEFAULT_UNIT_ID), intOf(s, "size", DEFAULT_SIZE));
		this.memory.delayMs(intOf(s, "delayMs", 0));
	}

	/** 설정 값(숫자·문자) → 정수 */
	public static int intOf(Map<String, Object> s, String key, int def) {
		Object v = s != null ? s.get(key) : null;
		if (v instanceof Number n) {
			return n.intValue();
		}
		return v != null && !String.valueOf(v).isBlank() ? Integer.parseInt(String.valueOf(v).trim()) : def;
	}

	public ModbusDevice memory() {
		return memory;
	}

	public int port() {
		return memory.port();
	}

	public int unitId() {
		return memory.unitId();
	}

	public String bindAddress() {
		return bindAddress;
	}

	public int connLimit() {
		return connLimit;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Protocol getProtocol() {
		return Protocol.MODBUS_TCP;
	}

	@Override
	public PlcStatus getStatus() {
		return status;
	}

	@Override
	public void setStatus(PlcStatus status) {
		this.status = status;
	}

	@Override
	public synchronized void start() throws VirtualPlcException {
		if (!attached) {
			ModbusPorts.get().attach(this);
			attached = true;
		}
		memory.silent(false);
		status = PlcStatus.RUNNING;
	}

	@Override
	public synchronized void stop() {
		if (attached) {
			ModbusPorts.get().detach(this);
			attached = false;
		}
		status = PlcStatus.STOPPED;
	}

	@Override
	public void pause() throws VirtualPlcException {
		requireAttached("일시정지");
		memory.silent(true);
		status = PlcStatus.PAUSED;
	}

	@Override
	public void resume() throws VirtualPlcException {
		requireAttached("재개");
		memory.silent(false);
		status = PlcStatus.RUNNING;
	}

	@Override
	public void release() {
		stop();
	}

	@Override
	public int dropConnections() {
		return attached ? ModbusPorts.get().dropConnections(port()) : 0;
	}

	/** 그 포트의 연결 수(같은 포트의 다른 단위 ID 장치와 함께 쓴다) */
	public int connections() {
		return attached ? ModbusPorts.get().connections(port()) : 0;
	}

	private void requireAttached(String what) throws VirtualPlcException {
		if (!attached) {
			throw new VirtualPlcException(VirtualPlcException.ERR_409, "실행 중이 아니라 " + what + "할 수 없습니다: " + name + " (" + status + ")");
		}
	}

	void connected(Client c) {
		if (notified.putIfAbsent(c.id(), c) == null) {
			ModbusPorts.history(this, c, true);
		}
	}

	void disconnected(Client c) {
		if (notified.remove(c.id()) != null) {
			ModbusPorts.history(this, c, false);
		}
	}

	/** 떼어 낼 때 — 남긴 연결을 모두 끊김으로 */
	void disconnectAll() {
		for (Client c : notified.values()) {
			disconnected(c);
		}
	}
}
