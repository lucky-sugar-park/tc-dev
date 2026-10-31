package com.wow.be.tc.plc.management;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition;
import com.wow.be.tc.ToolControlException;

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
	
	public abstract boolean publishPlcConnector(long id) throws ToolControlException;
	
	public abstract boolean publishPlcConnectorByName(String plcConnectorName) throws ToolControlException;
	
	public abstract boolean releasePlcConnector(long id) throws ToolControlException;
	
	public abstract boolean releasePlcConnectorByName(String plcConnectorName) throws ToolControlException;
	
	public abstract boolean pausePlcConnector(long id) throws ToolControlException;
	
	public abstract boolean pausePlcConnectorByName(String plcConnectorName) throws ToolControlException;
	
	public abstract boolean resumePlcConnector(long id) throws ToolControlException;
	
	public abstract boolean resumePlcConnectorByName(String plcConnectorName) throws ToolControlException;
	
	public abstract Map<String, String> testConnection(long id) throws ToolControlException;
	
	public abstract Map<String, String> testConnectionByName(String name) throws ToolControlException;
	
	public abstract Map<String, String> testConnectionByIpAddress(String ip, int port);
	
	public abstract void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo);
	
	public abstract List<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond, PageInfo pageInfo);
	
	public abstract long deletePlcConnectionEventHistories(Date timestamp);

}
