package com.wow.be.tc.vplc.management;

import java.util.Date;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@EqualsAndHashCode(callSuper=false)
@ToString
@Slf4j
public class ConnectionClientHistoryInfo extends AbstractInfo {

	private long seqNo;
	
	private String clientChannelId;
	
	private String vplcId;

	private int port;
	
	private String clientIp;
	
	private String connOrDisconn;
	
	private Date timestamp;
	
	private String description;
	
	public ConnectionClientHistoryInfo () {
		super();
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}
}
