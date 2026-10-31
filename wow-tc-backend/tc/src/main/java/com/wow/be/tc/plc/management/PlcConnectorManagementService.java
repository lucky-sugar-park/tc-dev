package com.wow.be.tc.plc.management;

import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.PlcResult;

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

	/**
	 * 인터페이스 없이 점을 바로 읽어 본다(화면의 시험 읽기, docs/plc-protocol-design.md §16). 게시된 커넥터만 — 다중화면 주인
	 * 인스턴스로 넘긴다. 점이 틀리면 IllegalArgumentException(400), 커넥터가 없으면 404, 게시 전이면 503.
	 */
	public abstract PlcResult testRead(String name, List<PointInfo> points) throws ToolControlException;

	/** OPC UA 노드 찾아보기(docs/opcua-design.md O10) — nodeId 를 비우면 Objects 폴더. OPC UA 커넥터가 아니면 400 */
	public abstract List<com.wow.be.tc.plc.connector.opcua.OpcUaTools.BrowseNode> browse(String name, String nodeId) throws ToolControlException;
	
	public abstract void savePlcConnectionEventHistory(PlcConnectionEventHistInfo pcehInfo);
	
	public abstract List<PlcConnectionEventHistInfo> searchPlcConnectionEventHist(Condition cond, PageInfo pageInfo);
	
	public abstract long deletePlcConnectionEventHistories(Date timestamp);

}
