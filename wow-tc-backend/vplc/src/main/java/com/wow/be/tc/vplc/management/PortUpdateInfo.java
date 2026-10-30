package com.wow.be.tc.vplc.management;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 가상 PLC 포트 변경 요청. vplcId 가 있으면 아이디로, 없으면 vplcName 으로 대상을 찾는다.
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class PortUpdateInfo extends AbstractInfo {

	private String vplcId;
	
	private String vplcName;
	
	private int startPort;
	
	private int portCount;
	
	private int portConnLimit;

	@Override
	public String toPlainText() {
		return "vplcId=" + vplcId + ", vplcName=" + vplcName + ", startPort=" + startPort
				+ ", portCount=" + portCount + ", portConnLimit=" + portConnLimit;
	}
}
