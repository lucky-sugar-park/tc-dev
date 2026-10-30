package com.wow.be.tc.vplc.machine;

import java.util.List;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.PlcStatus;

public class VirtualProgrammableControllerBuilder {
	
	private String id;

	private String name;
	
	private PlcStatus status;
	
	private FrameFormat frameFormat;

	private List<MemoryInfo> memInfos;
	
	private String ipAddress;

	private int startPort;

	private int portCount;

	private int connLimit;
	
	private String manufacturer;
	
	private String description;

	public VirtualProgrammableControllerBuilder () {
	}
	
	public VirtualProgrammableControllerBuilder withId(String id) {
		this.id=id;
		return this;
	}

	public VirtualProgrammableControllerBuilder withName(String name) {
		this.name=name;
		return this;
	}
	
	public VirtualProgrammableControllerBuilder withIpAddress(String ipAddress) {
		this.ipAddress=ipAddress;
		return this;
	}
	
	public VirtualProgrammableControllerBuilder withStatus(PlcStatus status) {
		this.status=status;
		return this;
	}
	
	public VirtualProgrammableControllerBuilder withFrameFormat(FrameFormat frameFormat) {
		this.frameFormat=frameFormat;
		return this;
	}

	public VirtualProgrammableControllerBuilder withMemoryTypes(List<MemoryInfo> memInfos) {
		this.memInfos=memInfos;
		return this;
	}

	public VirtualProgrammableControllerBuilder withStartPort(int startPort) {
		this.startPort=startPort;
		return this;
	}

	public VirtualProgrammableControllerBuilder withPortCount(int portCount) {
		this.portCount=portCount;
		return this;
	}

	public VirtualProgrammableControllerBuilder withConnLimit(int connLimit) {
		this.connLimit=connLimit;
		return this;
	}
	
	public VirtualProgrammableControllerBuilder withDescription(String description) {
		this.description=description;
		return this;
	}
	
	public VirtualProgrammableControllerBuilder withManufacturer(String manufacturer) {
		this.manufacturer=manufacturer;
		return this;
	}

	public VirtualProgrammableController build() {
		if(startPort==0) startPort=12345;
		if(portCount==0) portCount=1;
		if(connLimit==0) connLimit=1;
		if(frameFormat==null) frameFormat=FrameFormat.BINARY;
		
		VirtualProgrammableController plc=new VirtualProgrammableController(id, name, ipAddress, status, frameFormat, memInfos, startPort, portCount, connLimit);
		plc.setDescription(description);
		plc.setManufacturer(manufacturer);
		if(plc.getStatus()==null) {
			plc.setStatus(PlcStatus.PUBLISHED);
		}
		plc.setIpAddress(ipAddress);

		return plc;
	}
}
