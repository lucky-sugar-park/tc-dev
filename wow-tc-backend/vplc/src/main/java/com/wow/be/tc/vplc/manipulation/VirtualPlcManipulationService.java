package com.wow.be.tc.vplc.manipulation;

import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.machine.MemoryInfo;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;

/**
 * 메모리에 올라온 가상 PLC 의 제어(시작·정지·일시정지·재개)와 메모리 읽기·쓰기·초기화.
 */
public interface VirtualPlcManipulationService {
	
	public void publishVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void publishVirtualPlc(VirtualPlcInfo vplcInfo) throws VirtualPlcException;
	
	public void publishVirtualPlcByName(String vplcName) throws VirtualPlcException;

	public void startVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void startVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public void stopVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void stopVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public void pauseVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void pauseVirtualPlcByName(String vplcName) throws VirtualPlcException;

	public void resumeVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public void resumeVirtualPlcByName(String vplcName) throws VirtualPlcException;
	
	public void releaseVirtualPlc(String vplcId) throws VirtualPlcException;
	
	public MemoryInfo read(String vplcId, String memoryType, String frameFormat, int startAddress, int wordCount) throws VirtualPlcException;
	
	public MemoryInfo readByVplcName(String vplcName, String memoryType, String frameFormat, int startAddress, int wordCount) throws VirtualPlcException;

	public MemoryInfo write(String vplcId, String memoryType, String frameFormat, int startAddress, int wordCount, String data) throws VirtualPlcException;
	
	public MemoryInfo writeByVplcName(String vplcName, String memoryType, String frameFormat, int startAddress, int wordCount, String data) throws VirtualPlcException;
	
	public MemoryInfo clearAll(String vplcId, String memoryType, String frameFormat, int currentPosition) throws VirtualPlcException;
	
	public MemoryInfo clearAllByVplcName(String vplcName, String memoryType, String frameFormat, int currentPosition) throws VirtualPlcException;
}
