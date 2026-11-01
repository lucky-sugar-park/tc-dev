package com.wow.be.tc.vplc.modbus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.wow.be.tc.vplc.Types.Protocol;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.core.AbstractVirtualPlc;
import com.wow.be.tc.vplc.core.memory.AreaMemory;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;
import com.wow.be.tc.vplc.modbus.ModbusDevice.Table;
import com.wow.be.tc.vplc.modbus.ModbusTcpServer.Client;

/**
 * 가상 Modbus TCP 장치 — 포트 + 단위 ID, 네 영역(코일·이산 입력·입력 레지스터·보유 레지스터)을 메모리에 둔다(docs/virtual-devices.md).
 * 시작하면 {@link ModbusPorts} 의 그 포트에 붙고, 일시정지는 그 단위 ID 만 답하지 않기, 응답 지연은 설정 {@code delayMs}.
 * 영역 메모리({@link AreaMemory}) — HR·IR 은 WORD, CO·DI 는 BIT, 번호는 프로토콜 주소(0부터). 영역 값은 게시돼 있는 동안 남는다.
 */
public final class ModbusVirtualPlc extends AbstractVirtualPlc implements AreaMemory {

	/** 설정 기본값 */
	public static final int DEFAULT_UNIT_ID = 1;

	public static final int DEFAULT_SIZE = 10_000;

	private final String bindAddress;

	private final int connLimit;

	private final ModbusDevice memory;

	/** 이 장치가 연결로 남긴 연결(아이디 → 연결) */
	private final Map<String, Client> notified = new ConcurrentHashMap<>();

	public ModbusVirtualPlc(VirtualPlcInfo info) {
		super(info.getId(), info.getName());
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
	public Protocol getProtocol() {
		return Protocol.MODBUS_TCP;
	}

	@Override
	protected void doStart() throws VirtualPlcException {
		ModbusPorts.get().attach(this);
		memory.silent(false);
	}

	@Override
	protected void doStop() {
		ModbusPorts.get().detach(this);
		memory.silent(false);
	}

	@Override
	protected void doPause() {
		memory.silent(true);
	}

	@Override
	protected void doResume() {
		memory.silent(false);
	}

	@Override
	public int dropConnections() {
		return isRunning() ? ModbusPorts.get().dropConnections(port()) : 0;
	}

	/** 그 포트의 연결 수(같은 포트의 다른 단위 ID 장치와 함께 쓴다) */
	public int connections() {
		return isRunning() ? ModbusPorts.get().connections(port()) : 0;
	}

	// ---- 영역 메모리 ----

	@Override
	public List<Area> areas() {
		int n = memory.size();
		return List.of(new Area("hr", "HR", Unit.WORD, n, false, false), new Area("ir", "IR", Unit.WORD, n, false, false),
			new Area("co", "CO", Unit.BIT, n, false, false), new Area("di", "DI", Unit.BIT, n, false, false));
	}

	@Override
	public int[] read(String area, int start, int count) {
		Table t = Table.of(area);
		int[] out = new int[count];
		if (t.isBit()) {
			boolean[] b = memory.bits(t, start, count);
			for (int i = 0; i < count; i++) {
				out[i] = b[i] ? 1 : 0;
			}
		} else {
			short[] r = memory.registers(t, start, count);
			for (int i = 0; i < count; i++) {
				out[i] = r[i] & 0xFFFF;
			}
		}
		return out;
	}

	@Override
	public void write(String area, int start, int[] values) {
		Table t = Table.of(area);
		if (t.isBit()) {
			boolean[] b = new boolean[values.length];
			for (int i = 0; i < b.length; i++) {
				b[i] = values[i] != 0;
			}
			memory.setBits(t, start, b);
		} else {
			short[] r = new short[values.length];
			for (int i = 0; i < r.length; i++) {
				r[i] = (short) values[i];
			}
			memory.setRegisters(t, start, r);
		}
	}

	@Override
	public void clear(String area) {
		Table t = Table.of(area);
		if (t.isBit()) {
			memory.setBits(t, 0, new boolean[memory.size()]);
		} else {
			memory.setRegisters(t, 0, new short[memory.size()]);
		}
	}

	// ---- 연결 이력 ----

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
