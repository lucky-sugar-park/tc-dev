package com.wow.be.tc.plc.management.impl;

import java.util.Date;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.plc.management.PlcConnectionEventHistInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
// 색인: 기간 검색·보관 정리(docs/data-retention.md) — migration/016
@Table(name="PLC_CONNECTION_HIST", indexes={
	@Index(name="IX_PCH_EVENT_TIMESTAMP", columnList="EVENT_TIMESTAMP")
})
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class PlcConnectionEventHistEntity extends PersistentEntity<PlcConnectionEventHistInfo>{

	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="ID")
	private long id;
	
	@Column(name="PLC_CONNECTOR_ID", nullable=false)
	private long plcConnectorId;
	
	@Column(name="PLC_CONNECTOR_NAME", nullable=false)
	private String plcConnectorName;
	
	@Column(name="EVENT_TYPE")
	private String eventType;
	
	@Column(name="PLC_CONNECTOR_STATUS_1")
	private String status1;
	
	@Column(name="PLC_CONNECTOR_STATUS_2")
	private String status2;
	
	@Column(name="CAUSE", length=1024)
	private String cause;
	
	@Column(name="EVENT_TIMESTAMP")
	private Date timestamp;
	
	@Column(name="DESCRIPTION", length=1024)
	private String description;
	
	@Lob
	@Column(name="EVENT_JSON", columnDefinition="LONGTEXT")
	private String eventJson;
	
	public PlcConnectionEventHistEntity() {
		super();
	}

	@Override
	public PlcConnectionEventHistInfo info() {
		PlcConnectionEventHistInfo pcehInfo=new PlcConnectionEventHistInfo();
		pcehInfo.setId(this.getId());
		pcehInfo.setPlcConnectorId(this.getPlcConnectorId());
		pcehInfo.setPlcConnectorName(this.getPlcConnectorName());
		pcehInfo.setStatus1(this.getStatus1());
		pcehInfo.setStatus2(this.getStatus2());
		pcehInfo.setCause(this.getCause());
		pcehInfo.setEventType(this.getEventType());
		pcehInfo.setDescription(this.getDescription());
		pcehInfo.setEventJson(this.getEventJson());
		
		pcehInfo.setCreatedBy(super.getCreatedBy());
		pcehInfo.setCreatedDate(super.getCreatedDate());
		pcehInfo.setUpdatedBy(super.getUpdatedBy());
		pcehInfo.setUpdatedDate(super.getUpdatedDate());
		
		return pcehInfo;
	}

	@Override
	public void from(PlcConnectionEventHistInfo info) {
		this.setId(info.getId());
		this.setPlcConnectorId(info.getPlcConnectorId());
		this.setPlcConnectorName(info.getPlcConnectorName());
		this.setStatus1(info.getStatus1()!=null?info.getStatus1().name():"");
		this.setStatus2(info.getStatus2()!=null?info.getStatus2().name():"");
		this.setCause(info.getCause());
		this.setEventType(info.getEventType());
		this.setTimestamp(info.getTimestamp());
		this.setDescription(info.getDescription());
		this.setEventJson(info.getEventJson());
		
		super.setCreatedBy(info.getCreatedBy());
		super.setCreatedDate(info.getCreatedDate());
		super.setUpdatedBy(info.getUpdatedBy());
		super.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}
}