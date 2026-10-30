package com.wow.be.tc.plc;

import lombok.Data;

@Data
public class PlcReqAndResHolder {

	private PlcRequest request;
	
	private PlcResponse response;
}
