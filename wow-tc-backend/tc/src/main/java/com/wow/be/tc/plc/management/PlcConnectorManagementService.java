package com.wow.be.tc.plc.management;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition;

public interface PlcConnectorManagementService {

	public abstract void registerPlcConnector(PlcConnectorInfo plcConnectorInfo);
	
	public abstract void updatePlcConnector(PlcConnectorInfo plcConnectorInfo);
	
	public abstract void deletePlcConnectorById(long plcConnectorId);
	
	public abstract void deletePlcConnectorByName(String plcConnectorName);
		
	public abstract PlcConnectorInfo findPlcConnectorById(long id);
	
	public abstract PlcConnectorInfo findPlcConnectorByName(String name);
	
	public abstract List<PlcConnectorInfo> searchAllPlcConnectores();
	
	public abstract List<PlcConnectorInfo> searchAllPlcConnectores(PageInfo pageInfo);
	
	public abstract List<PlcConnectorInfo> searchPlcConnectores(Condition cond);
	
	public abstract List<PlcConnectorInfo> searchPlcConnectores(Condition cond, PageInfo pageInfo);
	
	public abstract boolean publishPlcConnector(long id);
	
	public abstract boolean publishPlcConnectorByName(String plcConnectorName);
	
	public abstract boolean releasePlcConnector(long id);
	
	public abstract boolean releasePlcConnectorByName(String plcConnectorName);
	
	public abstract boolean pausePlcConnector(long id);
	
	public abstract boolean pausePlcConnectorByName(String plcConnectorName);
	
	public abstract boolean resumePlcConnector(long id);
	
	public abstract boolean resumePlcConnectorByName(String plcConnectorName);
	
	public abstract Map<String, String> testConnection(long id);
	
	public abstract Map<String, String> testConnectionByName(String name);
	
	public abstract Map<String, String> testConnectionByIpAddress(String ip, int port);
	
	public abstract void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo);
	
	public abstract List<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond, PageInfo pageInfo);
	
	public abstract long deletePlcConnectionEventHistories(Date timestamp);

}
