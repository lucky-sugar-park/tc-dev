package com.wow.be.tc.plc.protocol.modbus;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modbus 점 주소 (docs/plc-protocol-design.md §6.2).
 *
 * <ul>
 *   <li>{@code HR100}·{@code IR5}·{@code CO0}·{@code DI3} — 영역 + 번호. 번호는 커넥터 설정 {@code addressBase}(기본 1)를
 *       따른다: 1 이면 {@code HR1} 이 프로토콜 주소 0, 0 이면 {@code HR0} 이 0.</li>
 *   <li>Modicon 표기 {@code 40001}(HR)·{@code 30001}(IR)·{@code 00001}(CO)·{@code 10001}(DI) — 5자리(…9999) 또는
 *       6자리(…65536). 늘 1부터 센다.</li>
 *   <li>레지스터 뒤 {@code .비트}(10진 0~15) — 레지스터 안의 비트 하나({@code HR1.15}, {@code 40001.3}, docs/word-bit-points.md).
 *       비트가 없으면 {@code bit} 는 -1.</li>
 * </ul>
 */
public record ModbusAddress(Table table, int offset, int bit) {

	public ModbusAddress(Table table, int offset) {
		this(table, offset, -1);
	}

	/** 레지스터 안의 비트인지 */
	public boolean isWordBit() {
		return bit >= 0;
	}

	/** 영역 — 레지스터(16비트) 둘, 비트 둘. 입력 영역은 읽기만 된다 */
	public enum Table {
		COIL(1, true, false), DISCRETE_INPUT(2, true, true), INPUT_REGISTER(4, false, true), HOLDING_REGISTER(3, false, false);

		/** 읽기 함수 코드 */
		final int readFunction;

		final boolean bit;

		final boolean readOnly;

		Table(int readFunction, boolean bit, boolean readOnly) {
			this.readFunction = readFunction;
			this.bit = bit;
			this.readOnly = readOnly;
		}

		public boolean isBit() {
			return bit;
		}

		public boolean isReadOnly() {
			return readOnly;
		}
	}

	private static final Pattern PREFIXED = Pattern.compile("(HR|IR|CO|DI)(\\d{1,5})");

	private static final Pattern MODICON = Pattern.compile("([0134])(\\d{4,5})");

	/**
	 * @param addressBase 0 또는 1 — 영역 + 번호 표기에만 쓴다
	 * @throws IllegalArgumentException 형식이 틀렸거나 범위(0~65535) 밖
	 */
	public static ModbusAddress parse(String text, int addressBase) {
		if (text == null || text.isBlank()) {
			throw new IllegalArgumentException("Modbus 주소가 없습니다");
		}
		String t = text.trim().toUpperCase(Locale.ROOT);
		int dot = t.indexOf('.');
		if (dot >= 0) {
			ModbusAddress word = parse(t.substring(0, dot), addressBase);
			String b = t.substring(dot + 1);
			if (word.table().isBit()) {
				throw new IllegalArgumentException("코일·이산 입력(" + word.table() + ")에는 .비트를 붙이지 않습니다 — " + text + " (레지스터만: HR1.15)");
			}
			int bit = b.matches("[0-9]{1,2}") ? Integer.parseInt(b) : -1;
			if (bit < 0 || bit > 15) {
				throw new IllegalArgumentException("Modbus 비트 번호는 10진 0~15 입니다: " + text);
			}
			return new ModbusAddress(word.table(), word.offset(), bit);
		}
		Matcher m = PREFIXED.matcher(t);
		if (m.matches()) {
			Table table = switch (m.group(1)) {
				case "HR" -> Table.HOLDING_REGISTER;
				case "IR" -> Table.INPUT_REGISTER;
				case "CO" -> Table.COIL;
				default -> Table.DISCRETE_INPUT;
			};
			return of(table, Integer.parseInt(m.group(2)) - addressBase, text);
		}
		m = MODICON.matcher(t);
		if (m.matches()) {
			Table table = switch (m.group(1)) {
				case "4" -> Table.HOLDING_REGISTER;
				case "3" -> Table.INPUT_REGISTER;
				case "0" -> Table.COIL;
				default -> Table.DISCRETE_INPUT;
			};
			return of(table, Integer.parseInt(m.group(2)) - 1, text);
		}
		throw new IllegalArgumentException("Modbus 주소는 HR100·IR5·CO0·DI3(영역+번호) 또는 40001·30001·00001·10001(Modicon), 레지스터 안 비트는 HR1.15 입니다: " + text);
	}

	private static ModbusAddress of(Table table, int offset, String text) {
		if (offset < 0 || offset > 0xFFFF) {
			throw new IllegalArgumentException("Modbus 주소가 범위(프로토콜 주소 0~65535) 밖입니다: " + text);
		}
		return new ModbusAddress(table, offset);
	}
}
