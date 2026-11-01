package com.wow.be.tc.vplc.management.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.ColumnDefault;

import com.wow.be.tc.vplc.Types.FrameFormat;
import com.wow.be.tc.vplc.Types.PlcStatus;
import com.wow.be.tc.vplc.Types.Protocol;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.wow.be.tc.vplc.machine.VirtualProgrammableController;
import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.vplc.management.VirtualPlcInfo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Builder
@Entity
@Table(name="VPLC")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class VirtualPlcEntity extends PersistentEntity<VirtualPlcInfo>{

	@Transient
	private VirtualProgrammableController vplc;

	@Id
	@Column(name="ID")
	private String id;

	@Column(name="NAME", unique=true)
	private String name;

	/** 종류 — 비어 있으면 MELSEC_MC(예전 줄) */
	@Column(name="PROTOCOL", length=20)
	private String protocol;

	/** 종류별 설정(JSON) */
	@Column(name="SETTINGS", length=2000)
	private String settings;

	@Column(name="STATUS")
	private String status;

	/**
	 * PLC 전체에 대해서 하나의 포맷만 적용 가능함
	 */
	@Column(name="FRAME_FORMAT")
	private String frameFormat;
	
	@Column(name="MANUFACTURER")
	private String manufacturer;
	
	@Column(name="IP_ADDRESS")
	private String ipAddress;
	
	@Column(name="START_PORT")
	private Integer startPort;
	
	@Column(name="PORT_COUNT")
	private Integer portCount;
	
	@Column(name="PORT_CONN_LIMIT")
	@ColumnDefault("1")
	private Integer portConnLimit;
	
	@Column(name="DESCRIPTION", length=1024)
	private String description;
	
	@Column(name="MEM_TYPES")
	private String memTypes;
	
	@Column(name="PUBLISHED")
	private Boolean published;
	
	@OneToMany(fetch=FetchType.LAZY, mappedBy="vplcEntity", cascade={ CascadeType.ALL } )
	private List<ConnectionEntity> connections;
	
	public VirtualPlcEntity () {
		super();
		connections=new ArrayList<>();
	}
	
	public VirtualPlcInfo status() {
		return this.info();
	}
	
	@Override
	public VirtualPlcInfo info() {
		VirtualPlcInfo info=new VirtualPlcInfo();
		info.setId(this.getId());
		info.setName(this.getName());
		info.setProtocol(Protocol.of(this.getProtocol()));
		if(this.getSettings()!=null && !this.getSettings().isBlank()) {
			Map<String, Object> m=new Gson().fromJson(this.getSettings(), new TypeToken<LinkedHashMap<String, Object>>(){}.getType());
			// Gson 은 숫자를 모두 double 로 읽는다 — 정수면 정수로
			m.replaceAll((k, v)->v instanceof Double d && d==Math.rint(d) && !Double.isInfinite(d) ? (Object)Long.valueOf(d.longValue()) : v);
			info.setSettings(m);
		}
		info.setFrameFormat(FrameFormat.valueOf(this.getFrameFormat()));
		info.setStatus(PlcStatus.valueOf(this.getStatus()));
		info.setIpAddress(this.getIpAddress());
		info.setStartPort(this.getStartPort()!=null?this.getStartPort():0);
		info.setPortCount(this.getPortCount()!=null?this.getPortCount():0);
		info.setPortConnLimit(this.getPortConnLimit()!=null?this.getPortConnLimit():0);
		info.setManufacturer(this.getManufacturer());
		info.setDescription(this.getDescription());
		
		String[] memTypeArr=this.memTypes.split(",");
		for(String memType:memTypeArr) {
			info.addMemoryType(memType);
		}
		
		info.setPublished(this.getPublished()==null?false:this.getPublished());
		
		this.getConnections().forEach(connEntity->{
			info.addConnectionInfo(connEntity.info());
		});
		
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());

		return info;
	}

	@Override
	public void from(VirtualPlcInfo info) {
		this.setId(info.getId());
		if(info.getName()!=null) {
			this.setName(info.getName());
		}
		this.setProtocol((info.getProtocol()!=null ? info.getProtocol() : Protocol.MELSEC_MC).name());
		this.setSettings(info.getSettings()==null || info.getSettings().isEmpty() ? null : new Gson().toJson(info.getSettings()));
		if(info.getFrameFormat()!=null) {
			this.setFrameFormat(info.getFrameFormat().name());
		}
		if(info.getStatus()!=null) {
			this.setStatus(info.getStatus().name());
		}
		if(info.getIpAddress()!=null) {
			this.setIpAddress(info.getIpAddress());
		}
		if(info.getStartPort()>0) {
			this.setStartPort(info.getStartPort());
		}
		if(info.getPortCount()>0) {
			this.setPortCount(info.getPortCount());
		}
		if(info.getPortConnLimit()>0) {
			this.setPortConnLimit(info.getPortConnLimit());
		}
		if(info.getManufacturer()!=null) {
			this.setManufacturer(info.getManufacturer());
		}
		if(info.getDescription()!=null) {
			this.setDescription(info.getDescription());
		}
		
		StringBuffer sb=new StringBuffer();
		info.getMemoryTypeList().forEach(memType->{
			sb.append(memType+",");
		});
		this.setMemTypes(sb.substring(0, sb.lastIndexOf(",")));
		
		this.setPublished(info.isPublished());
		
		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}
}
