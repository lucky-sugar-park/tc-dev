package com.wow.be.tc.vplc.melsec;

import java.util.List;

import com.wow.be.tc.vplc.melsec.MelsecTypes.FrameFormat;

public class MelsecVirtualPlcBuilder {
	
	private String id;

	private String name;
	
	
	private FrameFormat frameFormat;

	private List<MemoryInfo> memInfos;
	
	private String ipAddress;

	private int startPort;

	private int portCount;

	private int connLimit;
	
	private String manufacturer;
	
	private String description;

	public MelsecVirtualPlcBuilder () {
	}
	
	public MelsecVirtualPlcBuilder withId(String id) {
		this.id=id;
		return this;
	}

	public MelsecVirtualPlcBuilder withName(String name) {
		this.name=name;
		return this;
	}
	
	public MelsecVirtualPlcBuilder withIpAddress(String ipAddress) {
		this.ipAddress=ipAddress;
		return this;
	}
	
	public MelsecVirtualPlcBuilder withFrameFormat(FrameFormat frameFormat) {
		this.frameFormat=frameFormat;
		return this;
	}

	public MelsecVirtualPlcBuilder withMemoryTypes(List<MemoryInfo> memInfos) {
		this.memInfos=memInfos;
		return this;
	}

	public MelsecVirtualPlcBuilder withStartPort(int startPort) {
		this.startPort=startPort;
		return this;
	}

	public MelsecVirtualPlcBuilder withPortCount(int portCount) {
		this.portCount=portCount;
		return this;
	}

	public MelsecVirtualPlcBuilder withConnLimit(int connLimit) {
		this.connLimit=connLimit;
		return this;
	}
	
	public MelsecVirtualPlcBuilder withDescription(String description) {
		this.description=description;
		return this;
	}
	
	public MelsecVirtualPlcBuilder withManufacturer(String manufacturer) {
		this.manufacturer=manufacturer;
		return this;
	}

	public MelsecVirtualPlc build() {
		if(startPort==0) startPort=12345;
		if(portCount==0) portCount=1;
		if(connLimit==0) connLimit=1;
		if(frameFormat==null) frameFormat=FrameFormat.BINARY;
		
		MelsecVirtualPlc plc=new MelsecVirtualPlc(id, name, ipAddress, frameFormat, memInfos, startPort, portCount, connLimit);
		plc.setDescription(description);
		plc.setManufacturer(manufacturer);
		plc.setIpAddress(ipAddress);

		return plc;
	}
}
