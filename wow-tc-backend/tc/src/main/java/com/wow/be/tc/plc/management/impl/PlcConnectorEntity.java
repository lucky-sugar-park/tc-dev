package com.wow.be.tc.plc.management.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.Types.FrameFormat;
import com.wow.be.tc.Types.PlcConnectorInfoStatus;
import com.wow.be.tc.Types.PlcConnectorOperStatus;
import com.wow.be.tc.plc.management.PlcConnectorInfo;
import com.wow.be.tc.plc.protocol.PlcDrivers;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Builder
@Entity
@Table(name="PLC_CONNECTOR")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class PlcConnectorEntity extends PersistentEntity<PlcConnectorInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ID")
	private Long id;

	@Column(name="NAME", unique=true, nullable=false)
	private String name;

	@Column(name="MANUFACTURER")
	private String manufacturer;
	
	@Column(name="ADAPTER_CLASS_NAME")
	private String adapterClassName;
	
	@Column(name="IS_PUBLISHED")
	private Boolean published;
	
	// PlcInfoStatus
	@Column(name="STATUS_1")
	private String status1;
	
	// PlcOperStatus
	@Column(name="STATUS_2")
	private String status2;

	@Column(name="HOST_IP")
	private String hostIp;

	@Column(name="PORTS")
	private String ports;
	
	@Column(name="CONN_LIMIT")
	private Integer connLimitPerPort;

	@Column(name="CONNECTION_VIPS")
	private String connectionVips;

	@Column(name="FRAME_FORMAT")
	private String messageFrameFormat;

	@Column(name="MEM_TYPES")
	private String memTypes;

	/** MELSEC_MC, MODBUS_TCP, OPC_UA — 비면 MELSEC_MC(이 칼럼 전에 등록한 커넥터) */
	@Column(name="PROTOCOL", length=32)
	private String protocol;

	/** 프로토콜별 설정 JSON(docs/plc-protocol-design.md §4.1) */
	@Lob
	@Column(name="SETTINGS_JSON", columnDefinition="LONGTEXT")
	private String settingsJson;

	@Column(name="DESCRIPTION", length=1024)
	private String description;
	
	public PlcConnectorEntity() {
		super();
	}

	@SuppressWarnings("unchecked")
	@Override
	public PlcConnectorInfo info() {
		PlcConnectorInfo info=new PlcConnectorInfo();
		info.setId(this.id);
		info.setName(this.name);
		info.setManufacturer(this.manufacturer);
		info.setAdapterClassName(this.adapterClassName);
		info.setPublished(this.published!=null?this.published:false);
		info.setStatus1(PlcConnectorInfoStatus.valueOf(this.status1));
		info.setStatus2(PlcConnectorOperStatus.valueOf(this.status2));
		info.setHostIp(this.hostIp);
		
		for(String port: split(this.ports)) {
			info.addPort(Integer.parseInt(port));
		}
		
		info.setConnLimitPerPort(this.getConnLimitPerPort()!=null?this.getConnLimitPerPort():-1);
		
		for(String vip: split(this.connectionVips)) {
			info.addVip(vip);
		}
		
		info.setMessageFrameFormat(FrameFormat.toFrameFormat(messageFrameFormat));
		ObjectMapper om=new ObjectMapper();
		
		// Modbus 커넥터처럼 메모리 종류를 적지 않은 행은 NULL 이다
		for(String memType: split(this.memTypes)) {
			info.addMemType(memType);
		}
		
		info.setProtocol(this.protocol!=null && !this.protocol.isBlank()?this.protocol:PlcDrivers.DEFAULT_PROTOCOL);
		Map<String, Object> settings=new LinkedHashMap<>();
		try {
			if(this.settingsJson!=null && !this.settingsJson.isBlank()) {
				settings=om.readValue(this.settingsJson, Map.class);
			}
		} catch (JsonProcessingException e) {
			log.warn("커넥터 설정 JSON 을 읽지 못했습니다: {}", this.name);
		}
		info.setSettings(settings);

		info.setDescription(this.description);
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());
		return info;
	}

	/** 쉼표로 나눈 칼럼 — NULL·빈 칸은 빈 목록 */
	private static java.util.List<String> split(String column) {
		java.util.List<String> out=new java.util.ArrayList<>();
		if(column!=null) {
			for(String v: column.split(",")) {
				if(!v.isBlank()) {
					out.add(v.trim());
				}
			}
		}
		return out;
	}

	@Override
	public void from(PlcConnectorInfo info) {
		// 등록 요청의 id 는 0(long 기본값)이다. 0 을 그대로 넣으면 Hibernate 가 없는 행을 merge 하려다
		// "Row was already updated or deleted" 로 실패하므로, InterfaceEntity 처럼 새 행은 null 로 둔다.
		this.setId(info.getId()>0?info.getId():null);
		if(info.getName()!=null && !"".equals(info.getName())) {
			this.setName(info.getName());
		}
		if(info.getManufacturer()!=null && !"".equals(info.getManufacturer())) {
			this.setManufacturer(info.getManufacturer());
		}
		if(info.getAdapterClassName()!=null && !"".equals(info.getAdapterClassName())) {
			this.setAdapterClassName(info.getAdapterClassName());
		}
		this.setPublished(info.isPublished());
		if(info.getStatus1()!=null && !"".equals(info.getStatus1().name())) {
			this.setStatus1(info.getStatus1().name());
		}
		if(info.getStatus2()!=null && !"".equals(info.getStatus2().name())) {
			this.setStatus2(info.getStatus2().name());
		}
		if(info.getHostIp()!=null && !"".equals(info.getHostIp())) {
			this.setHostIp(info.getHostIp());
		}

		StringBuilder sb=new StringBuilder();
		if(info.getPorts().size()>0) {
			for(int port:info.getPorts()) {
				sb.append(port+",");
			}
			String portStr=sb.toString();
			this.setPorts(portStr.substring(0,portStr.length()-1));
		}
		
		if(info.getConnLimitPerPort()>0) {
			this.setConnLimitPerPort(info.getConnLimitPerPort()>0?info.getConnLimitPerPort():-1);
		}
		
		if(info.getConnectionVips().size()>0) {
			sb=new StringBuilder();
			for(String vip:info.getConnectionVips()) {
				sb.append(vip+",");
			}
			String vipStr=sb.toString();
			this.setConnectionVips(vipStr.substring(0, vipStr.length()-1));
		}
		
		if(info.getMessageFrameFormat()!=null) { 
			this.setMessageFrameFormat(info.getMessageFrameFormat().getName());
		}
		
		ObjectMapper om=new ObjectMapper();
		
		if(info.getMemTypes()!=null && info.getMemTypes().size()>0) {
			sb=new StringBuilder();
			for(String memType:info.getMemTypes()) {
				sb.append(memType+",");
			}
			String memTypeArr=sb.toString();
			this.setMemTypes(memTypeArr.substring(0, memTypeArr.length()-1));
		}

		if(info.getProtocol()!=null && !info.getProtocol().isBlank()) {
			this.setProtocol(info.getProtocol());
		}
		if(info.getSettings()!=null) {
			try {
				this.setSettingsJson(om.writeValueAsString(info.getSettings()));
			} catch (JsonProcessingException e) {
				throw new IllegalArgumentException("커넥터 설정을 저장할 수 없습니다: " + e.getMessage());
			}
		}
		
		if(info.getDescription()!=null && !"".equals(info.getDescription())) {
			this.setDescription(info.getDescription());
		}
		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
