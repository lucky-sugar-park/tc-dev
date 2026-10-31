package com.wow.be.tc.plc.protocol.melsec;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * MELSEC(Q/L 계열) 디바이스 — MC 프로토콜 3E 디바이스 코드(BINARY 1바이트, ASCII 2문자), 비트/워드, 번호 진법.
 * 번호 진법은 GX Works 표기와 같다(X·Y·B·W·SB·SW·DX·DY 는 16진, 나머지는 10진).
 */
public enum MelsecDevice {

	// 비트 디바이스
	SM("SM", 0x91, true, false),
	X("X*", 0x9C, true, true),
	Y("Y*", 0x9D, true, true),
	M("M*", 0x90, true, false),
	L("L*", 0x92, true, false),
	F("F*", 0x93, true, false),
	V("V*", 0x94, true, false),
	B("B*", 0xA0, true, true),
	S("S*", 0x98, true, false),
	SB("SB", 0xA1, true, true),
	DX("DX", 0xA2, true, true),
	DY("DY", 0xA3, true, true),
	TS("TS", 0xC1, true, false),
	TC("TC", 0xC0, true, false),
	SS("SS", 0xC7, true, false),
	SC("SC", 0xC6, true, false),
	CS("CS", 0xC4, true, false),
	CC("CC", 0xC3, true, false),
	// 워드 디바이스
	SD("SD", 0xA9, false, false),
	D("D*", 0xA8, false, false),
	W("W*", 0xB4, false, true),
	SW("SW", 0xB5, false, true),
	TN("TN", 0xC2, false, false),
	SN("SN", 0xC8, false, false),
	CN("CN", 0xC5, false, false),
	Z("Z*", 0xCC, false, false),
	R("R*", 0xAF, false, false),
	ZR("ZR", 0xB0, false, false);

	private final String asciiCode;

	private final byte binaryCode;

	private final boolean bit;

	private final boolean hexNumber;

	MelsecDevice(String asciiCode, int binaryCode, boolean bit, boolean hexNumber) {
		this.asciiCode = asciiCode;
		this.binaryCode = (byte) binaryCode;
		this.bit = bit;
		this.hexNumber = hexNumber;
	}

	public String asciiCode() {
		return asciiCode;
	}

	public byte binaryCode() {
		return binaryCode;
	}

	public boolean isBit() {
		return bit;
	}

	public boolean isHexNumber() {
		return hexNumber;
	}

	/** 기호가 긴 것부터(SM 을 S 로, ZR 을 Z 로 잘못 읽지 않게) */
	static final List<MelsecDevice> BY_SYMBOL_LENGTH = Arrays.stream(values())
		.sorted(Comparator.comparingInt((MelsecDevice d) -> d.name().length()).reversed())
		.toList();
}
