package com.wow.be.tc.plc.management;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.Types.FrameFormat;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.Types.PlcConnectorOperStatus;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper=false)
public class PlcConnectorInfo extends AbstractInfo {
	
	private long id;

	private String name;

	private String manufacturer;
	
	private String adapterClassName;
	
	private boolean published;
	
	/**
	 * REGISTERED | PUBLISHED | RELEASED | UNKNOWN
	 */
	private PlcConnectorInfoStatus status1;
	
	/**
	 * CONNECTION_TEST_OK | CONNECTION_TEST_ERROR | CONNECTION_TEST_NONE | RUNNING | STOPPED | PAUSED | ERROR | UNKNOWN
	 */
	private PlcConnectorOperStatus status2;

	private String hostIp;

	private List<Integer> ports;
	
	private int connLimitPerPort;

	private List<String> connectionVips;

	private FrameFormat messageFrameFormat;

	private Map<String, String> commProtocolHeadersReqTemplate;
	
	private Map<String, String> commProtocolHeadersResTemplate;
	
	private List<String> memTypes;

	/** {@code MELSEC_MC}(기본), {@code MODBUS_TCP}, {@code OPC_UA} — docs/plc-protocol-design.md §4.1 */
	private String protocol;

	/** 프로토콜별 설정. 등록·수정 때 드라이버가 검증하고 기본값을 채운다 */
	private Map<String, Object> settings;

	private String description;

	public PlcConnectorInfo() {
		this.ports=new ArrayList<>();
		connectionVips=new ArrayList<>();
		commProtocolHeadersReqTemplate=new HashMap<>();
		memTypes=new ArrayList<>();
	}

	/**
	 * 지금 연결 상태(Redis) — 조회 API 가 응답할 때만 채운다. 아무 인스턴스도 연결하지 않았으면 null.
	 * {@code status1} 은 운영자 의도(게시·해제), 이것은 실제 연결이다(docs/plc-ha-design.md §3.4). 저장하지 않는다.
	 */
	private PlcConnectionState connection;

	public void addPort(int port) {
		ports.add(port);
	}

	public void addVip(String vip) {
		connectionVips.add(vip);
	}

	public void addHeader(String name, String value) {
		commProtocolHeadersReqTemplate.put(name, value);
	}
	
	public String getHeader(String name) {
		return commProtocolHeadersReqTemplate.get(name);
	}
	
	public void addMemType(String memType) {
		memTypes.add(memType);
	}

	@Override
	public String toPlainText() {
		return new StringBuffer().toString();
	}
}
