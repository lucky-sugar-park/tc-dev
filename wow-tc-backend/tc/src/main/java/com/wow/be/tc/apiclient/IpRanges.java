package com.wow.be.tc.apiclient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * IP·CIDR 목록 검사 (예: {@code 10.0.1.15}, {@code 10.0.1.0/24}, {@code fd00::/8}). 이름 해석(DNS)은 하지 않는다 —
 * 숫자 주소만 받는다.
 */
public final class IpRanges {

	private IpRanges() {
	}

	/** 목록이 비어 있으면 모든 주소를 허용한다 */
	public static boolean contains(List<String> ranges, String address) {
		if (ranges == null || ranges.isEmpty()) {
			return true;
		}
		byte[] target = toBytes(address);
		if (target == null) {
			return false;
		}
		for (String range : ranges) {
			if (matches(range, target)) {
				return true;
			}
		}
		return false;
	}

	/** 등록할 때 쓰는 형식 검사 */
	public static boolean isValid(String range) {
		if (range == null || range.isBlank()) {
			return false;
		}
		String[] parts = range.trim().split("/", 2);
		byte[] base = toBytes(parts[0]);
		if (base == null) {
			return false;
		}
		if (parts.length == 1) {
			return true;
		}
		try {
			int prefix = Integer.parseInt(parts[1]);
			return prefix >= 0 && prefix <= base.length * 8;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean matches(String range, byte[] target) {
		String[] parts = range.trim().split("/", 2);
		byte[] base = toBytes(parts[0]);
		if (base == null || base.length != target.length) {
			return false;
		}
		int prefix = base.length * 8;
		if (parts.length == 2) {
			try {
				prefix = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				return false;
			}
		}
		int fullBytes = prefix / 8;
		for (int i = 0; i < fullBytes; i++) {
			if (base[i] != target[i]) {
				return false;
			}
		}
		int rest = prefix % 8;
		if (rest == 0) {
			return true;
		}
		int mask = (0xFF << (8 - rest)) & 0xFF;
		return (base[fullBytes] & mask) == (target[fullBytes] & mask);
	}

	/** 숫자 주소만 바이트로 바꾼다. 이름이면 null (InetAddress 가 DNS 를 찾지 않게 먼저 거른다) */
	private static byte[] toBytes(String address) {
		if (address == null) {
			return null;
		}
		String a = address.trim();
		if (a.isEmpty() || !a.matches("[0-9a-fA-F:.]+")) {
			return null;
		}
		try {
			byte[] bytes = InetAddress.getByName(a).getAddress();
			// IPv4 를 IPv6 로 감싼 주소(::ffff:a.b.c.d)는 IPv4 로 본다
			if (bytes.length == 16 && isIpv4Mapped(bytes)) {
				return new byte[] { bytes[12], bytes[13], bytes[14], bytes[15] };
			}
			return bytes;
		} catch (UnknownHostException e) {
			return null;
		}
	}

	private static boolean isIpv4Mapped(byte[] b) {
		for (int i = 0; i < 10; i++) {
			if (b[i] != 0) {
				return false;
			}
		}
		return (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF;
	}
}
