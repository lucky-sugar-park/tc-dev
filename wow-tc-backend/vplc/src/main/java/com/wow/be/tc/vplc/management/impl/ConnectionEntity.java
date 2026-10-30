package com.wow.be.tc.vplc.management.impl;

import java.util.ArrayList;
import java.util.List;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.vplc.management.ConnectionInfo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name="CONNECTION")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class ConnectionEntity extends PersistentEntity<ConnectionInfo>{
	
	@EmbeddedId
	private ConnectionEntityId connectionEntityId;
	
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="VPLC_ID", referencedColumnName="ID", nullable=false, updatable=false, insertable=false)
	private VirtualPlcEntity vplcEntity;
	
	@Column(name="CONNECTED_COUNT")
	private Integer connectedCount;
	
	@Column(name="CONNECTED")
	private Boolean connected;
	
	@OneToMany(fetch=FetchType.LAZY, mappedBy="connectionEntity", cascade={ CascadeType.ALL } )
	private List<ConnectionClientEntity> connectedClientList;
	
	public ConnectionEntity () {
		super();
		connectedClientList=new ArrayList<>();
	}
	
	public void addConnectionClientEntity(ConnectionClientEntity clientEntity) {
		this.connectedClientList.add(clientEntity);
	}

	@Override
	public ConnectionInfo info() {
		ConnectionInfo info=new ConnectionInfo();
		info.setVplcId(this.connectionEntityId.getVplcId());
		info.setPort(this.connectionEntityId.getPort());
		info.setConnectedCount(this.getConnectedClientList().size());
		info.setConnected(info.getConnectedCount()>0?true:false);
		
		connectedClientList.forEach(connClient->{
			info.addConnectedClient(connClient.info());
		});
		
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());

		return info;
	}

	@Override
	public void from(ConnectionInfo info) {
		ConnectionEntityId connectionEntityId=new ConnectionEntityId();
		connectionEntityId.setVplcId(info.getVplcId());
		connectionEntityId.setPort(info.getPort());
		
		this.setConnectionEntityId(connectionEntityId);
		this.setConnected(info.isConnected());
		this.setConnectedCount(info.getConnectedCount());
		
		info.getConnectedClientList().forEach(clientInfo->{
			ConnectionClientEntity clientEntity=new ConnectionClientEntity();
			clientEntity.from(clientInfo);
			this.addConnectionClientEntity(clientEntity);
		});
		
		super.setCreatedBy(info.getCreatedBy());
		super.setCreatedDate(info.getCreatedDate());
		super.setUpdatedBy(info.getUpdatedBy());
		super.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return new StringBuffer().toString();
	}
}
