package com.wow.be.tc.schedule;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.quartz.Job;

import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.LegacyClassNames;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ScheduleJobInfo extends AbstractInfo {

	private String id;
	
	private String name;
	
	private String schedulerName;
	
	private Boolean use;
	
	private Class<Job> jobClass;
	
	private String jobClassName;
	
	private String groupName;
	
	private String triggerState=ScheduleConstants.TRIGGER_STATE_NONE;
	
	private Date stateChangedTime=new Date();
	
	private String triggerType=ScheduleConstants.TRIGGER_TYPE_CRON;
	
	private Long startDelay;
	
	private Long repeatInterval;
	
	private String cronExpression;
	
	private List<ScheduleJobDetailInfo> jobDetails;
	
	private String description;

	// 아래는 목록 응답에만 채운다(저장하지 않는다) — ScheduleJobService.searchScheduleJobsWithStatus

	/** 종류({@link ScheduleJobKind} 이름) */
	private String kind;

	private Date lastExecutionTime;

	/** SUCCESS·FAIL */
	private String lastResult;

	private String lastRemark;

	/** 마지막으로 실행한 Quartz 인스턴스 */
	private String lastInstanceId;

	private Long lastElapsedMs;

	/** Quartz 트리거의 다음 실행(예약되지 않았으면 null) */
	private Date nextFireTime;

	/** 최근 {@code tc.schedule.recent-fail-hours} 동안 실패 수 */
	private Long recentFailCount;
	
	public ScheduleJobInfo() {
		jobDetails=new ArrayList<>();
	}
	
	@SuppressWarnings("unchecked")
	public void setJobClassName(String jobClassName) {
		this.jobClassName = jobClassName;
		try {
			this.setJobClass((Class<Job>) (LegacyClassNames.forName(jobClassName)));
		} catch (ClassNotFoundException e) {
			// 등록할 수 없는 클래스 — 저장할 때 ScheduleJobValidator 가 막는다
			this.setJobClass(null);
		}
	}
	
	public void addJobDetail(ScheduleJobDetailInfo jobDetail) {
		jobDetails.add(jobDetail);
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
