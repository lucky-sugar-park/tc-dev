package com.wow.be.tc.vplc.machine;

import lombok.Data;

@Data
public class WordInfo {

	private short  word;
	private byte   high;
	private byte   low;
	private String hlStr;
	private String lhStr;

	public WordInfo() {

	}

	public WordInfo(byte low, byte high) {
		this.word=(short)((high<<8)|(low&0xFF));
		this.high=high;
		this.low =low;
		this.hlStr=this.getAsciiAsHighLow();
		this.lhStr=this.getAsciiAsLowHigh();
	}

	public WordInfo(short word) {
		this.word=word;
		this.high=(byte)((word>>8)&(0xFF));
		this.low=(byte)(word&0xFF);
		this.hlStr=this.getAsciiAsHighLow();
		this.lhStr=this.getAsciiAsLowHigh();
	}

	public String getAsciiAsLowHigh() {
		return new String(new byte[] { low, high });
	}

	public String getAsciiAsHighLow() {
		return new String(new byte[] { high, low });
	}
	
	public byte[] toBytes() {
		return new byte[] { low, high };
	}
}

