package com.wow.be.tc.plc.management;


import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;

public interface PlcConnectionEventHistoryService {
	
	public void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo);
	
	public PageResponse<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond);
}
