package com.wow.be.tc.plc.connector;

import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wow.be.tc.Types.PlcConnectorOperStatus;

import lombok.Data;

@Data
public class PlcConnectorEvent {
	
	public enum EventType { START, STOP, SHUTDOWN, CONNECT, DISCONNECT }
	
	private long plcConnectorId;
	
	private String plcConnectorName;
	
	private EventType type;
	
	private PlcConnectorOperStatus operStatus;
	
	private int activeChannelCount;
	
	private String remoteIp;
	
	private int remotePort;
	
	private String localIp;
	
	private int localPort;
	
	private Date timestamp;
	
	// disconnected인 경우: 연결이 끊긴 이유, connected인 경우: new, retry 
	private String cause;
	
	private String description;
	
	public String json(boolean pretty) {
		Gson gson=pretty?new GsonBuilder().setPrettyPrinting().create():new GsonBuilder().create();
		return gson.toJson(this);
	}
}
