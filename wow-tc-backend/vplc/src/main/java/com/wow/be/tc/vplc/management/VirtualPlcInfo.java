package com.wow.be.tc.vplc.management;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.machine.MemoryInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper=false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VirtualPlcInfo extends AbstractInfo {
	
	private String id;

	private String name;
	
	private PlcStatus status;
	
	/**
	 * PLC 전체에 대해서 하나의 포맷만 적용 가능함
	 */
	private FrameFormat frameFormat;
	
	private String manufacturer;
	
	private String ipAddress;
	
	private int startPort;
	
	private int portCount;
	
	private int portConnLimit;
	
	private String description;
	
	private List<String> memoryTypeList;
	
	private boolean published;
	
	private List<MemoryInfo> memInfos;
	
	private List<ConnectionInfo> connections;
	
	public VirtualPlcInfo() {
		super();
		memInfos=new ArrayList<>();
		connections=new ArrayList<>();
		memoryTypeList=new ArrayList<>();
	}
	
	public void addMemoryType(String memoryType) {
		this.memoryTypeList.add(memoryType);
	}
	
	public void addMemoryInfo(MemoryInfo memInfo) {
		this.memInfos.add(memInfo);
	}
	
	public void addConnectionInfo(ConnectionInfo connInfo) {
		this.connections.add(connInfo);
	}

	@Override
	public String toPlainText() {
		// TODO Auto-generated method stub
		return null;
	}
}
