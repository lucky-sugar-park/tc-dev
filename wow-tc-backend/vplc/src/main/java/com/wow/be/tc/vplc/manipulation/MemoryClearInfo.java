package com.wow.be.tc.vplc.manipulation;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 메모리 전체 초기화 요청 (레거시 VplcClearAllRequest). vplcId 가 있으면 아이디로, 없으면 vplcName 으로 대상을 찾는다.
 * currentPosition 은 초기화 뒤 다시 읽어 돌려줄 시작 주소다.
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class MemoryClearInfo extends AbstractInfo {

	private String vplcId;
	
	private String vplcName;
	
	private String memoryType;
	
	private String frameFormat;
	
	private int currentPosition;

	@Override
	public String toPlainText() {
		return "vplcId=" + vplcId + ", vplcName=" + vplcName + ", memoryType=" + memoryType
				+ ", frameFormat=" + frameFormat + ", currentPosition=" + currentPosition;
	}
}
