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

	/** 가상 PLC 이름 — 이력 찾기가 채운다(지운 가상 PLC 는 비어 있다) */
	private String vplcName;

	/** 가상 PLC 종류 — 이력 찾기가 채운다 */
	private String protocol;

	public ConnectionClientHistoryInfo () {
		super();
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}
}
