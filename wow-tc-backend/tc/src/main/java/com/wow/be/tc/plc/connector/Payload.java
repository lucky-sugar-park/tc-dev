package com.wow.be.tc.plc.connector;

import lombok.Data;

@Data
public class Payload {

	private long timestamp;
	
	private String frameFormat;
	
	private byte[] data;
}
