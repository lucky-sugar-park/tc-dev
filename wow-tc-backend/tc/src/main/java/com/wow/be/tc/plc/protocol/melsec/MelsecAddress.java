package com.wow.be.tc.plc.protocol.melsec;

import java.util.Locale;

/**
 * MELSEC 주소 — 디바이스 기호 + 번호({@code D100}, {@code M10}, {@code X1F}, {@code ZR20000}).
 * 번호는 디바이스의 진법으로 읽는다. 비트 디바이스의 번호는 비트 번호, 워드 디바이스는 워드 번호다.
 * 워드 디바이스 뒤에 {@code .비트}(16진 한 자리 0~F, GX Works 표기)를 붙이면 그 워드 안의 비트 하나다 — {@code D100.A}
 * (docs/word-bit-points.md). 비트가 없으면 {@code bit} 는 -1.
 */
public record MelsecAddress(MelsecDevice device, int number, int bit) {

	/** 3E 프레임의 디바이스 번호는 3바이트 */
	static final int MAX_NUMBER = 0xFFFFFF;

	public MelsecAddress(MelsecDevice device, int number) {
		this(device, number, -1);
	}

	/** 워드 디바이스 안의 비트인지 */
	public boolean isWordBit() {
		return bit >= 0;
	}

	/** @throws IllegalArgumentException 읽을 수 없는 주소(400) */
	public static MelsecAddress parse(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("주소가 없습니다");
		}
		String a = address.trim().toUpperCase(Locale.ROOT);
		int dot = a.indexOf('.');
		if (dot >= 0) {
			MelsecAddress word = parse(a.substring(0, dot));
			String b = a.substring(dot + 1);
			if (word.device().isBit()) {
				throw new IllegalArgumentException("비트 디바이스(" + word.device() + ")에는 .비트를 붙이지 않습니다 — " + address
					+ " (워드 디바이스만: D100.3)");
			}
			if (!b.matches("[0-9A-F]")) {
				throw new IllegalArgumentException("MELSEC 비트 번호는 16진 한 자리(0~F)입니다: " + address + " (10번 비트는 .A)");
			}
			return new MelsecAddress(word.device(), word.number(), Integer.parseInt(b, 16));
		}
		for (MelsecDevice device : MelsecDevice.BY_SYMBOL_LENGTH) {
			String symbol = device.name();
			if (!a.startsWith(symbol) || a.length() == symbol.length()) {
				continue;
			}
			String digits = a.substring(symbol.length());
			try {
				int number = Integer.parseInt(digits, device.isHexNumber() ? 16 : 10);
				if (number < 0 || number > MAX_NUMBER) {
					throw new IllegalArgumentException("주소 번호가 범위 밖입니다: " + address);
				}
				return new MelsecAddress(device, number);
			} catch (NumberFormatException e) {
				// 다른 기호일 수 있다(예: 'SD' 로 시작했지만 숫자가 아님) — 계속 찾는다
			}
		}
		throw new IllegalArgumentException("MELSEC 주소를 읽을 수 없습니다: " + address
			+ " (예: D100, W1A, M10, X1F, ZR20000, D100.3 — 16진 번호 디바이스: X Y B W SB SW DX DY)");
	}

	@Override
	public String toString() {
		String word = device.name() + (device.isHexNumber() ? Integer.toHexString(number).toUpperCase(Locale.ROOT) : Integer.toString(number));
		return isWordBit() ? word + "." + Integer.toHexString(bit).toUpperCase(Locale.ROOT) : word;
	}
}
