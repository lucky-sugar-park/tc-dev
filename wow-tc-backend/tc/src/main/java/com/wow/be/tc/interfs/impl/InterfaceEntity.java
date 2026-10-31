package com.wow.be.tc.interfs.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.PointInfo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
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
@Table(name="INTERFACE")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class InterfaceEntity extends PersistentEntity<InterfaceInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ID")
	private Long id;
	
	@Column(name="NAME", unique=true)
	private String name;
	
	@Column(name="PLC_NAME")
	private String plcName;
	
	@Column(name="IF_TYPE")
	private String interfaceType;
	
	@Column(name="IN_USE")
	private Boolean use;
	
	@Column(name="DESCRIPTION", length=2048)
	private String description;

	@Column(name="READ_BACK")
	private Boolean readBack;

	/** 인터페이스 settings(JSON) — 커넥터 설정을 덮어쓰는 것과 인터페이스 전용(docs/interface-detail-design.md §3). 비면 커넥터 설정 그대로 */
	@Column(name="SETTINGS_JSON", columnDefinition="LONGTEXT")
	private String settingsJson;

	@OneToMany(fetch=FetchType.LAZY, mappedBy="interfs", cascade={ CascadeType.REMOVE })
	private List<InterfacePointEntity> points;
	
	public InterfaceEntity() {
		super();
		points=new ArrayList<>();
	}

	@Override
	public InterfaceInfo info() {
		InterfaceInfo info=new InterfaceInfo();
		info.setId(this.getId()!=null && this.getId()>0?this.getId():-1);
		info.setName(this.getName());
		info.setPlcName(this.getPlcName());
		info.setInterfaceType(InterfaceType.valueOf(this.getInterfaceType()));
		info.setUse(this.getUse()==null?false:this.getUse());
		info.setDescription(this.getDescription());
		info.setReadBack(this.readBack!=null && this.readBack);
		Map<String, Object> settings=new LinkedHashMap<>();
		try {
			if(this.settingsJson!=null && !this.settingsJson.isBlank()) {
				settings=new ObjectMapper().readValue(this.settingsJson, Map.class);
			}
		} catch (JsonProcessingException e) {
			log.warn("인터페이스 설정 JSON 을 읽지 못했습니다: {}", this.name);
		}
		info.setSettings(settings);
		List<PointInfo> pointInfos=new ArrayList<>();
		for(InterfacePointEntity pentity:points) {
			pointInfos.add(pentity.info());
		}
		pointInfos.sort((a,b)->Integer.compare(a.getOrder(), b.getOrder()));
		info.setPoints(pointInfos);
		
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());
		
		return info;
	}

	@Override
	public void from(InterfaceInfo info) {
		this.setId(info.getId()>0?info.getId():null);
		
		if(info.getName()!=null && !"".equals(info.getName())) {
			this.setName(info.getName());
		}
		
		if(info.getPlcName()!=null && !"".equals(info.getPlcName())) {
			this.setPlcName(info.getPlcName());
		}
		if(info.getInterfaceType()!=null) {
			this.setInterfaceType(info.getInterfaceType().name());
		}

		this.setUse(info.isUse());

		if(info.getDescription()!=null && !"".equals(info.getDescription())) {
			this.setDescription(info.getDescription());
		}
		this.setReadBack(info.isReadBack());
		try {
			this.setSettingsJson(info.getSettings()!=null && !info.getSettings().isEmpty()
				? new ObjectMapper().writeValueAsString(info.getSettings()) : null);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("인터페이스 설정을 저장할 수 없습니다: " + e.getMessage());
		}
		
		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return new StringBuffer().toString();
	}
}
