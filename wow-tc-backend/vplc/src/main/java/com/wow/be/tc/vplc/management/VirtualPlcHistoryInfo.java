package com.wow.be.tc.vplc.management;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@EqualsAndHashCode(callSuper=false)
@Slf4j
public class VirtualPlcHistoryInfo extends AbstractInfo {

	private long seqNo;
	
	private String plcId;
	
	private String plcName;
	
	private String status;
	
	private String historyContents;

	@Override
	public String toPlainText() {
		log.debug("");
		return new StringBuffer().toString();
	}
}
