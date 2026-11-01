package com.wow.be.tc.vplc.modbus;

/**
 * 가상 Modbus 장치 하나(포트 + 단위 ID) — 네 영역을 메모리에 둔다. 번호는 프로토콜 주소(0부터).
 *
 * <ul>
 *   <li>코일(CO)·이산 입력(DI): 비트, 입력 레지스터(IR)·보유 레지스터(HR): 16비트.</li>
 *   <li>{@code delayMs} 만큼 늦게 답하고, {@code silent} 면 답하지 않는다 — tc 의 시간 초과·재연결 시험용.</li>
 * </ul>
 * 영역 접근은 이 객체로 잠근다(요청 처리와 REST 가 같은 장치를 만진다).
 */
public final class ModbusDevice {

	public enum Table { COIL, DISCRETE_INPUT, INPUT_REGISTER, HOLDING_REGISTER;

		public boolean isBit() {
			return this == COIL || this == DISCRETE_INPUT;
		}

		/** REST 경로의 짧은 이름 — co, di, ir, hr */
		public static Table of(String s) {
			return switch (s.toLowerCase()) {
				case "co", "coil", "coils" -> COIL;
				case "di", "discrete", "discrete_input" -> DISCRETE_INPUT;
				case "ir", "input", "input_register" -> INPUT_REGISTER;
				case "hr", "holding", "holding_register" -> HOLDING_REGISTER;
				default -> throw new IllegalArgumentException("영역은 co·di·ir·hr 입니다: " + s);
			};
		}
	}

	private final String name;

	private final int port;

	private final int unitId;

	private final int size;

	private final boolean[] coils;

	private final boolean[] discrete;

	private final short[] input;

	private final short[] holding;

	private volatile long delayMs;

	private volatile boolean silent;

	public ModbusDevice(String name, int port, int unitId, int size) {
		if (size < 1 || size > 65536) {
			throw new IllegalArgumentException("size 는 1~65536 입니다: " + size);
		}
		this.name = name;
		this.port = port;
		this.unitId = unitId;
		this.size = size;
		this.coils = new boolean[size];
		this.discrete = new boolean[size];
		this.input = new short[size];
		this.holding = new short[size];
	}

	public String name() {
		return name;
	}

	public int port() {
		return port;
	}

	public int unitId() {
		return unitId;
	}

	public int size() {
		return size;
	}

	public long delayMs() {
		return delayMs;
	}

	public void delayMs(long delayMs) {
		this.delayMs = Math.max(0, delayMs);
	}

	public boolean silent() {
		return silent;
	}

	public void silent(boolean silent) {
		this.silent = silent;
	}

	/** 범위 안인가 */
	public boolean inRange(int start, int count) {
		return start >= 0 && count >= 1 && start + count <= size;
	}

	public synchronized boolean[] bits(Table t, int start, int count) {
		boolean[] src = t == Table.COIL ? coils : discrete;
		boolean[] out = new boolean[count];
		System.arraycopy(src, start, out, 0, count);
		return out;
	}

	public synchronized void setBits(Table t, int start, boolean[] values) {
		boolean[] dst = t == Table.COIL ? coils : discrete;
		System.arraycopy(values, 0, dst, start, values.length);
	}

	public synchronized short[] registers(Table t, int start, int count) {
		short[] src = t == Table.HOLDING_REGISTER ? holding : input;
		short[] out = new short[count];
		System.arraycopy(src, start, out, 0, count);
		return out;
	}

	public synchronized void setRegisters(Table t, int start, short[] values) {
		short[] dst = t == Table.HOLDING_REGISTER ? holding : input;
		System.arraycopy(values, 0, dst, start, values.length);
	}
}
