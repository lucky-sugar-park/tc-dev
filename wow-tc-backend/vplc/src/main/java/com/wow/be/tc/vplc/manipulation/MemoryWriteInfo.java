package com.wow.be.tc.vplc.manipulation;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 메모리 쓰기 요청 (레거시 VplcWriteRequest). vplcId 가 있으면 아이디로, 없으면 vplcName 으로 대상을 찾는다.
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class MemoryWriteInfo extends AbstractInfo {

	private String vplcId;
	
	private String vplcName;
	
	private String memoryType;
	
	private String frameFormat;
	
	private int startAddress;
	
	private int wordCount;
	
	/**
	 * data.length()==wordCount<<1
	 */
	private String data;

	/**
	 * 워드 값(부호 있는 16비트 -32768~32767 또는 0~65535). 있으면 data 대신 쓰고 wordCount 는 그 길이다 — data 는 문자열 바이트라
	 * 숫자 값(예: D100=1234)을 쓸 수 없었다.
	 */
	private int[] words;

	@Override
	public String toPlainText() {
		return "vplcId=" + vplcId + ", vplcName=" + vplcName + ", memoryType=" + memoryType
				+ ", frameFormat=" + frameFormat + ", startAddress=" + startAddress + ", wordCount=" + wordCount
				+ ", data=" + data + ", words=" + java.util.Arrays.toString(words);
	}
}
