package com.wow.be.tc.vplc.manipulation;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 메모리 읽기 요청 (레거시 VplcReadRequest). vplcId 가 있으면 아이디로, 없으면 vplcName 으로 대상을 찾는다.
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class MemoryReadInfo extends AbstractInfo {

	private String vplcId;
	
	private String vplcName;
	
	private String memoryType;
	
	private String frameFormat;
	
	private int startAddress;
	
	private int wordCount;

	@Override
	public String toPlainText() {
		return "vplcId=" + vplcId + ", vplcName=" + vplcName + ", memoryType=" + memoryType
				+ ", frameFormat=" + frameFormat + ", startAddress=" + startAddress + ", wordCount=" + wordCount;
	}
}
