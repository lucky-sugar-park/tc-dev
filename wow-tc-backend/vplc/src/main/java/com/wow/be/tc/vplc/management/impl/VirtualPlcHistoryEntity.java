package com.wow.be.tc.vplc.management.impl;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.vplc.management.VirtualPlcHistoryInfo;

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
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Builder
@Entity
@Table(name="VPLC_HISTORY")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class VirtualPlcHistoryEntity extends PersistentEntity<VirtualPlcHistoryInfo> {

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="SEQ_NO")
	private long seqNo;

	@Column(name="PLC_ID")
	private String plcId;

	@Column(name="PLC_NAME")
	private String plcName;

	@Column(name="STATUS")
	private String status;

	// @Lob 만 두면 MariaDB 에서 TINYTEXT(255바이트)로 만들어져 등록 이력(PLC JSON)이 들어가지 않는다.
	// 이미 만들어진 테이블은 ddl-auto=update 가 타입을 바꾸지 않는다 — migration/001_long_text_longtext.sql
	@Lob
	@Column(name="HIST_CONTENTS", columnDefinition="LONGTEXT")
	private String historyContents;

	@Override
	public VirtualPlcHistoryInfo info() {
		VirtualPlcHistoryInfo info=new VirtualPlcHistoryInfo();
		info.setSeqNo(this.getSeqNo());
		info.setPlcId(this.getPlcId());
		info.setPlcName(this.getPlcName());
		info.setStatus(this.getStatus());
		info.setHistoryContents(this.getHistoryContents());

		return info;
	}

	@Override
	public void from(VirtualPlcHistoryInfo info) {
		this.setSeqNo(info.getSeqNo());
		this.setPlcId(info.getPlcId());
		this.setPlcName(info.getPlcName());
		this.setStatus(info.getStatus());
		this.setHistoryContents(info.getHistoryContents());
	}

	@Override
	public String toPlainText() {
		log.debug("");

		StringBuffer sb=new StringBuffer();
		sb.append("seqNo: " + this.getSeqNo() + "\n");
		sb.append("plcId: " + this.getPlcId() + "\n");
		sb.append("plcName: " + this.getPlcName() + "\n");
		sb.append("status: " + this.getStatus() + "\n");
		sb.append("historyContents: " + this.getHistoryContents() + "\n");

		return sb.toString();
	}
}
