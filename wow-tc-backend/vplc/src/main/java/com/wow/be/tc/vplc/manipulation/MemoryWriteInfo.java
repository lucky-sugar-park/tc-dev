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

	@Override
	public String toPlainText() {
		return "vplcId=" + vplcId + ", vplcName=" + vplcName + ", memoryType=" + memoryType
				+ ", frameFormat=" + frameFormat + ", startAddress=" + startAddress + ", wordCount=" + wordCount
				+ ", data=" + data;
	}
}
