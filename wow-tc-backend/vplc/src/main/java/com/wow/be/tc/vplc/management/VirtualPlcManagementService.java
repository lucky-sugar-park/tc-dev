package com.wow.be.tc.vplc.management;

import java.util.List;

import com.wow.be.framework.Condition;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.VirtualPlcException;

/**
 * 가상 PLC 등록·수정·게시·해제·삭제·조회와 연결 이력 관리.
 */
public interface VirtualPlcManagementService {
	
	public void registerVirtualPlc (VirtualPlcInfo vplcInfo) throws VirtualPlcException;
	
	public void updateVirtualPlc (VirtualPlcInfo vplcInfo) throws VirtualPlcException;
	
	public void updateVirtualPlcPort (String vplcId, int startPort, int portCount, int portConnLimit) throws VirtualPlcException;
	
	public void updateVirtualPlcPortByName (String vplcName, int startPort, int portCount, int portConnLimit) throws VirtualPlcException;
	
	public VirtualPlcInfo updateVirtualPlcStatus(String vplcId, String vplcName, PlcStatus plcStatus, boolean isPublished) throws VirtualPlcException;
	
	public void publishVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void publishVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public void releaseVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void releaseVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public void deleteVirtualPlc (String vplcId) throws VirtualPlcException;
	
	public void deleteVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public VirtualPlcInfo findVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public VirtualPlcInfo findVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public List<VirtualPlcInfo> searchAllVirtualPlc() throws VirtualPlcException;
	
	public List<VirtualPlcInfo> searchVirtualPlc(Condition cond) throws VirtualPlcException;
	
	public List<VirtualPlcHistoryInfo> searchVirtualPlcHistory(Condition cond) throws VirtualPlcException;
	
	public List<ConnectionClientHistoryInfo> searchConnectionClientHistory(Condition cond) throws VirtualPlcException;

	/** 연결 이력 한 쪽 — 가상 PLC 이름·IP, 종류, 기간으로 찾는다. 최신순, 항목에 가상 PLC 이름·종류를 채운다 */
	public ConnectionHistoryPage queryConnectionHistories(ConnectionHistoryQuery query);
	
	public List<ConnectionInfo> searchConnections(String vplcId) throws VirtualPlcException;
	
	public List<ConnectionInfo> searchConnectionsByVplcName(String vplcName) throws VirtualPlcException;
	
	public void saveConnectionClient(ConnectionClientInfo ccInfo) throws VirtualPlcException;

	/** 기동 때 — 붙어 있던 연결을 모두 지운다(다시 띄우면 연결은 모두 새로 붙는다. 종료 때의 끊김은 DB 가 닫혀 남기지 못한다) */
	public void resetConnectedClients();
		
}
