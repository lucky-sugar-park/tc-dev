package com.wow.be.tc.vplc.machine;

import java.util.Date;

import lombok.Data;

@Data
public class ClientConnectionEvent {
	
	private String vplcId;
	
	private int port;
	
	private String clientIpAddress;
	
	private String channelId;
	
	private String connOrDisconn;
	
	private Date timestamp;
	
	private String description;

}
