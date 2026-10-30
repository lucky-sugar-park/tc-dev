package com.wow.be.tc.vplc.management.impl;

import java.util.Date;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.vplc.management.ConnectionClientHistoryInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name="CONNECTED_CLIENT_HIST")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@Slf4j
public class ConnectionClientHistoryEntity extends PersistentEntity<ConnectionClientHistoryInfo> {
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="SEQ_NO")
	private Long seqNo;

	@Column(name="VPLC_ID")
	private String vplcId;
	
	@Column(name="PORT")
	private Integer port;
	
	@Column(name="CLIENT_CHANNEL_ID")
	private String clientChannelId;

	@Column(name="CLIENT_IP_ADDRESS")
	private String clientIp;
	
	@Column(name="CONN_OR_DISCONN")
	private String connOrDisconn;
	
	@Column(name="TIMESTAMP")
	private Date timestamp;
	
	@Column(name="DESCRIPTION")
	private String description;
	
	public ConnectionClientHistoryEntity () {
		super();
	}
	
	@Override
	public ConnectionClientHistoryInfo info() {
		ConnectionClientHistoryInfo info=new ConnectionClientHistoryInfo();
		info.setSeqNo(this.getSeqNo());
		info.setVplcId(this.getVplcId());
		info.setPort(this.getPort());
		info.setClientChannelId(this.clientChannelId);
		info.setClientIp(this.clientIp);
		info.setConnOrDisconn(this.connOrDisconn);
		info.setTimestamp(this.timestamp);
		info.setDescription(this.description);

		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());

		return info;
	}

	@Override
	public void from(ConnectionClientHistoryInfo info) {
		if(info.getSeqNo()!=0) {
			this.setSeqNo(info.getSeqNo());
		}
		this.setVplcId(info.getVplcId());
		this.setPort(info.getPort());
		this.setClientChannelId(info.getClientChannelId());
		this.setClientIp(info.getClientIp());
		this.setConnOrDisconn(info.getConnOrDisconn());
		this.setTimestamp(info.getTimestamp());
		this.setDescription(info.getDescription());

		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getUpdatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		log.debug("");
		return null;
	}
}
