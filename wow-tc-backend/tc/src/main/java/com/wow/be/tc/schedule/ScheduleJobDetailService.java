package com.wow.be.tc.schedule;

import java.util.List;

public interface ScheduleJobDetailService {

	// schedule job detail create
	public abstract void createScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail);
		
	// schedule job detail retrieve
	public abstract List<ScheduleJobDetailInfo> searchScheduleJobDetails(ScheduleJobInfo scheduleJob);
	public abstract List<ScheduleJobDetailInfo> searchScheduleJobDetails(String scheduleJobId);
		
	// schedule job detail update
	public abstract void updateScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail);
		
	// schedule job detail delete
	public abstract void deleteScheduleJobDetails(ScheduleJobInfo scheduleJob);
	public abstract void deleteScheduleJobDetails(String scheduleJobId);
	public abstract void deleteScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail);
	public abstract void deleteScheduleJobDetail(String scheduleJobId, String entryKey);
}
