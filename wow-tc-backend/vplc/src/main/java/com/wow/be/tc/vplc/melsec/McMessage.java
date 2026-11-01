package com.wow.be.tc.vplc.melsec;

import java.util.HexFormat;

import com.wow.be.tc.vplc.melsec.transport.McCodec.McRequest;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Postbox 로 넘기는 MC 요청(해석된 것) 또는 보낼 응답 프레임 — 요청이면 {@link #request}, 응답이면 {@link #frame}.
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class McMessage extends Message {

	private final McRequest request;

	private final byte[] frame;

	public McMessage(McRequest request, byte[] frame) {
		this.request = request;
		this.frame = frame;
	}

	@Override
	public String forString() {
		return frame != null ? HexFormat.of().formatHex(frame) : String.valueOf(request);
	}

	@Override
	public void extract(byte[] data) {
	}

	@Override
	public byte[] toByteArray() {
		return frame;
	}

	@Override
	public String toJson() {
		return "{\"mc\":\"" + forString() + "\"}";
	}
}
