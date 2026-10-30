package com.wow.be.tc.user.impl;

import java.util.Date;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.user.UserHistoryInfo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name="USER_HIST")
@Data
@EqualsAndHashCode(callSuper=false)
public class UserHistoryEntity extends PersistentEntity<UserHistoryInfo> {

	private static final long serialVersionUID = 1L;
	
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="SEQ_NO")
	private Long seqNo;

	@Column(name="USER_ID", nullable=false)
	private String userId;

	@Column(name="OPER", nullable=false)
	private String oper;

	@Column(name="TIMESTAMP")
	private Date timestamp;

	@Column(name="IP_ADDR")
	private String ipAddress;

	@Lob
	@Column(name="REMARK", columnDefinition="LONGTEXT")
	private String remark;
	
	public UserHistoryEntity() {
		super();
	}

	@Override
	public UserHistoryInfo info() {
		UserHistoryInfo usInfo=new UserHistoryInfo();
		usInfo.setSeqNo(this.getSeqNo());
		usInfo.setUserId(this.getUserId());
		usInfo.setOper(this.getOper());
		usInfo.setTimestamp(this.getTimestamp());
		usInfo.setIpAddress(this.getIpAddress());
		usInfo.setRemark(this.getRemark());
		
		usInfo.setCreatedBy(super.getCreatedBy());
		usInfo.setCreatedDate(super.getCreatedDate());
		usInfo.setUpdatedBy(super.getUpdatedBy());
		usInfo.setUpdatedDate(super.getUpdatedDate());
		
		return usInfo;
	}

	@Override
	public void from(UserHistoryInfo info) {
		if(info.getSeqNo()!=null) this.setSeqNo(info.getSeqNo());
		if(info.getUserId()!=null) this.setUserId(info.getUserId());
		this.setOper(info.getOper());
		this.setTimestamp(info.getTimestamp());
		this.setIpAddress(info.getIpAddress());
		this.setRemark(info.getRemark());

		this.setCreatedBy(info.getCreatedBy());
		this.setCreatedDate(info.getCreatedDate());
		this.setUpdatedBy(info.getUpdatedBy());
		this.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		StringBuilder sb = new StringBuilder();
		return sb.toString();
	}
}
