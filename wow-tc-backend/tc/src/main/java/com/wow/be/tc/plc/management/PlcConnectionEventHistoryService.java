package com.wow.be.tc.plc.management;

import java.util.Date;

import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;

public interface PlcConnectionEventHistoryService {
	
	public void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo);
	
	public PageResponse<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond);

	public long deleteByTimestamp(Date timestamp);
}
