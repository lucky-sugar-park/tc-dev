package com.wow.be.tc.user;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wow.be.framework.AbstractInfo;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Data
@EqualsAndHashCode(callSuper=false)
public class UserHistoryInfo extends AbstractInfo {
	
	private Long seqNo;

	private String userId;

	private String oper;

	private Date timestamp;

	private String ipAddress;

	private String remark;
	
	public UserHistoryInfo() {
		super();
	}

	@Override
	public String toPlainText() {
		StringBuilder sb = new StringBuilder();
		return sb.toString();
	}
}
