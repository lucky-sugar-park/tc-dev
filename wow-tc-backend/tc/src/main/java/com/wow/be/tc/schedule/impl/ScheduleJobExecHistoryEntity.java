package com.wow.be.tc.schedule.impl;

import java.util.Date;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.schedule.ScheduleJobExecHistoryInfo;

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

@ToString
@Builder
@Entity
@Table(name="SCHEDULE_JOB_EXEC_HIST")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class ScheduleJobExecHistoryEntity extends PersistentEntity<ScheduleJobExecHistoryInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="SEQ_NO")
	private Long seqNo;
	
	@Column(name="JOB_NAME")
	private String jobName;
	
	@Column(name="EXEC_TIME")
	private Date executionTime;
	
	@Column(name="EXEC_RESULT")
	private String executionResult;
	
	@Column(name="NEXT_FIRE_TIME")
	private Date nextFireTime;
	
	// 성공한 실행은 PLC 응답 전체가 들어간다. VARCHAR(255)였을 때는 "Data too long" 으로 성공 이력이 하나도 저장되지 않았다.
	// 이미 만들어진 표는 migration/003_schedule_hist_remark_longtext.sql 로 넓힌다.
	@Lob
	@Column(name="REMARK", columnDefinition="LONGTEXT")
	private String remark;
	
	public ScheduleJobExecHistoryEntity() {
		super();
	}
	
	@Override
	public ScheduleJobExecHistoryInfo info() {
		ScheduleJobExecHistoryInfo execHist=new ScheduleJobExecHistoryInfo();
		execHist.setSeqNo(this.getSeqNo());
		execHist.setJobName(this.getJobName());
		execHist.setExecutionTime(this.getExecutionTime());
		execHist.setExecutionResult(this.getExecutionResult());
		execHist.setNextFireTime(this.getNextFireTime());
		execHist.setRemark(this.getRemark());
		execHist.setCreatedBy(super.getCreatedBy());
		execHist.setCreatedDate(super.getCreatedDate());
		execHist.setUpdatedBy(super.getUpdatedBy());
		execHist.setUpdatedDate(super.getUpdatedDate());

		return execHist;
	}

	@Override
	public void from(ScheduleJobExecHistoryInfo info) {
		// 새 이력(seqNo 0)은 null 로 둬야 IDENTITY 로 채번된다 — 0 을 넣으면 없는 행을 merge 하려다 실패한다
		this.setSeqNo(info.getSeqNo()>0?info.getSeqNo():null);
		this.setJobName(info.getJobName());
		this.setExecutionTime(info.getExecutionTime());
		this.setExecutionResult(info.getExecutionResult());
		this.setNextFireTime(info.getNextFireTime());
		this.setRemark(info.getRemark());
		super.setCreatedBy(info.getCreatedBy());
		super.setCreatedDate(info.getCreatedDate());
		super.setUpdatedBy(info.getUpdatedBy());
		super.setUpdatedDate(info.getUpdatedDate());
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
